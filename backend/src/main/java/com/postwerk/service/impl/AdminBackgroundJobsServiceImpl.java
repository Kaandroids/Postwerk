package com.postwerk.service.impl;

import com.postwerk.dto.admin.BackgroundJobsKpisResponse;
import com.postwerk.dto.admin.JobDetailResponse;
import com.postwerk.dto.admin.JobQueueResponse;
import com.postwerk.dto.admin.JobResponse;
import com.postwerk.dto.admin.JobRunResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.JobRun;
import com.postwerk.model.enums.ApprovalStatus;
import com.postwerk.repository.AutomationDelayedEmailRepository;
import com.postwerk.repository.JobRunRepository;
import com.postwerk.repository.PendingActionRepository;
import com.postwerk.service.AdminBackgroundJobsService;
import com.postwerk.service.AuditService;
import com.postwerk.service.DataRetentionService;
import com.postwerk.service.JobRunService;
import com.postwerk.service.ScheduledEmailSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdminBackgroundJobsService}. The job registry is the platform's 4 real recurring
 * jobs; last-run/duration/counts come from the {@code job_runs} history (written by {@link JobRunService}),
 * next-run is derived from each schedule, and queue depths are live (pending-action + delayed-email
 * tables). Run-now triggers the real job on a background thread.
 *
 * @since 1.0
 */
@Service
public class AdminBackgroundJobsServiceImpl implements AdminBackgroundJobsService {

    private static final Logger log = LoggerFactory.getLogger(AdminBackgroundJobsServiceImpl.class);

    private static final String Q_APPROVAL = "approval-queue";
    private static final String Q_DELAYED = "delayed-queue";

    /** Static definition of one recurring job. {@code intervalSeconds < 0} → cron (daily 02:00 UTC). */
    private record JobDef(String id, String name, String type, String scheduleHuman,
                          long intervalSeconds, String description, String drainsQueueId) {}

    private static final List<JobDef> JOBS = List.of(
            new JobDef(ScheduledEmailSyncService.JOB_ID, "Email-sync scheduler", "Scheduler", "every 5 min",
                    300, "Pulls new mail from every read-enabled IMAP mailbox across all tenants.", null),
            new JobDef(AutomationExecutorServiceImpl.JOB_POLLER, "Automation poller", "Scheduler", "every 60s",
                    60, "Syncs accounts with active automations and processes new emails; drains the approval queue.", Q_APPROVAL),
            new JobDef(AutomationExecutorServiceImpl.JOB_DELAYED, "Delayed-email processor", "Worker", "every 30s",
                    30, "Resumes automation flows for delayed emails once their delay elapses.", Q_DELAYED),
            new JobDef(DataRetentionService.JOB_ID, "Data-retention sweep", "Maintenance", "daily 02:00",
                    -1, "GDPR/DSGVO retention: prunes expired emails, users and conversations, and pseudonymizes IPs.", null));

    private static JobDef def(String id) {
        return JOBS.stream().filter(j -> j.id().equals(id)).findFirst().orElse(null);
    }

    private final JobRunRepository jobRunRepository;
    private final JobRunService jobRunService;
    private final PendingActionRepository pendingActionRepository;
    private final AutomationDelayedEmailRepository delayedEmailRepository;
    private final AuditService auditService;
    private final ScheduledEmailSyncService emailSync;
    private final AutomationExecutorServiceImpl automationExecutor;
    private final DataRetentionService dataRetention;

    public AdminBackgroundJobsServiceImpl(JobRunRepository jobRunRepository,
                                          JobRunService jobRunService,
                                          PendingActionRepository pendingActionRepository,
                                          AutomationDelayedEmailRepository delayedEmailRepository,
                                          AuditService auditService,
                                          ScheduledEmailSyncService emailSync,
                                          AutomationExecutorServiceImpl automationExecutor,
                                          DataRetentionService dataRetention) {
        this.jobRunRepository = jobRunRepository;
        this.jobRunService = jobRunService;
        this.pendingActionRepository = pendingActionRepository;
        this.delayedEmailRepository = delayedEmailRepository;
        this.auditService = auditService;
        this.emailSync = emailSync;
        this.automationExecutor = automationExecutor;
        this.dataRetention = dataRetention;
    }

    // ── List ────────────────────────────────────────────────────────────────
    @Override
    public List<JobResponse> listJobs() {
        Map<String, long[]> counts = counts24h();
        Instant now = Instant.now();
        return JOBS.stream().map(d -> buildJob(d, counts, now)).toList();
    }

    @Override
    public BackgroundJobsKpisResponse kpis() {
        Map<String, long[]> counts = counts24h();
        Instant now = Instant.now();
        List<JobResponse> jobs = JOBS.stream().map(d -> buildJob(d, counts, now)).toList();

        long runs24h = counts.values().stream().mapToLong(c -> c[0]).sum();
        long failed24h = counts.values().stream().mapToLong(c -> c[1]).sum();
        long queueDepth = approvalPending() + delayedPending();
        long paused = jobs.stream().filter(j -> "paused".equals(j.status())).count();
        long failing = jobs.stream().filter(j -> "failing".equals(j.status())).count();

        List<Long> durations = jobs.stream().map(JobResponse::lastDurationMs).filter(java.util.Objects::nonNull).toList();
        Long avg = durations.isEmpty() ? null
                : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0));
        Long nextMin = jobs.stream().map(JobResponse::nextRunAt).filter(java.util.Objects::nonNull)
                .map(t -> Math.max(0, Duration.between(now, t).toMinutes())).min(Long::compareTo).orElse(null);

        return new BackgroundJobsKpisResponse(JOBS.size(), runs24h, failed24h, queueDepth, avg, nextMin, paused, failing);
    }

    // ── Queues ────────────────────────────────────────────────────────────────
    @Override
    public List<JobQueueResponse> queues() {
        long pending = approvalPending();
        long approved = countStatus(ApprovalStatus.APPROVED);
        long rejected = countStatus(ApprovalStatus.REJECTED);
        JobQueueResponse approval = new JobQueueResponse(Q_APPROVAL, "Approval queue",
                AutomationExecutorServiceImpl.JOB_POLLER, pending, pending > 100 ? "backlog" : "clear",
                List.of(new JobQueueResponse.Breakdown("Pending", pending, pending > 0 ? "warn" : "ok"),
                        new JobQueueResponse.Breakdown("Approved", approved, "ok"),
                        new JobQueueResponse.Breakdown("Rejected", rejected, "danger")));

        long delayed = delayedPending();
        JobQueueResponse delayedQ = new JobQueueResponse(Q_DELAYED, "Delayed-email queue",
                AutomationExecutorServiceImpl.JOB_DELAYED, delayed, delayed > 100 ? "backlog" : "clear",
                List.of(new JobQueueResponse.Breakdown("Awaiting send", delayed, delayed > 0 ? "warn" : "ok")));

        return List.of(approval, delayedQ);
    }

    // ── Detail ──────────────────────────────────────────────────────────────
    @Override
    public JobDetailResponse getJob(String id) {
        JobDef d = requireDef(id);
        JobResponse job = buildJob(d, counts24h(), Instant.now());
        List<JobRunResponse> runs = jobRunRepository.findTop20ByJobIdOrderByStartedAtDesc(id).stream()
                .map(this::toRunResponse).toList();
        return new JobDetailResponse(job, runs);
    }

    // ── Actions (INFRA_MANAGE) ──────────────────────────────────────────────
    @Override
    public JobResponse runNow(String id, UUID actorUserId, String ip) {
        JobDef d = requireDef(id);
        // Fire-and-forget — the job records its own run; the request returns the pre-run row.
        CompletableFuture.runAsync(() -> {
            try {
                switch (id) {
                    case ScheduledEmailSyncService.JOB_ID -> emailSync.runNow(actorUserId);
                    case AutomationExecutorServiceImpl.JOB_POLLER -> automationExecutor.runPollNow(actorUserId);
                    case AutomationExecutorServiceImpl.JOB_DELAYED -> automationExecutor.runDelayedNow(actorUserId);
                    case DataRetentionService.JOB_ID -> dataRetention.runNow(actorUserId);
                    default -> { /* unreachable — validated above */ }
                }
            } catch (Exception e) {
                log.warn("Manual run of job {} failed: {}", id, e.getMessage());
            }
        });
        auditService.log(actorUserId, AuditAction.JOB_RUN_TRIGGERED, "Triggered manual run of " + d.name(), ip);
        return buildJob(d, counts24h(), Instant.now());
    }

    @Override
    public JobResponse setPaused(String id, boolean paused, UUID actorUserId, String ip) {
        JobDef d = requireDef(id);
        jobRunService.setPaused(id, paused);
        auditService.log(actorUserId, paused ? AuditAction.JOB_PAUSED : AuditAction.JOB_RESUMED,
                (paused ? "Paused schedule: " : "Resumed schedule: ") + d.name(), ip);
        return buildJob(d, counts24h(), Instant.now());
    }

    // ── Building ────────────────────────────────────────────────────────────
    private JobResponse buildJob(JobDef d, Map<String, long[]> counts, Instant now) {
        JobRun last = jobRunRepository.findTopByJobIdOrderByStartedAtDesc(d.id());
        boolean paused = jobRunService.isPaused(d.id());
        String status = paused ? "paused"
                : (last != null && !last.isOk() ? "failing" : "healthy");
        Instant nextRun = paused ? null : nextRunAt(d, last, now);
        long[] c = counts.getOrDefault(d.id(), new long[]{0, 0});
        return new JobResponse(
                d.id(), d.name(), d.type(), d.scheduleHuman(), status,
                last != null ? last.getStartedAt() : null,
                last != null ? last.isOk() : null,
                last != null ? last.getDurationMs() : null,
                nextRun,
                last != null ? last.getItemsProcessed() : null,
                c[0], c[1], d.description(), d.drainsQueueId());
    }

    /** Interval jobs: last + interval (or now + interval if never run). Cron: next 02:00 UTC. */
    private static Instant nextRunAt(JobDef d, JobRun last, Instant now) {
        if (d.intervalSeconds() < 0) {
            Instant todayRun = LocalDate.now(ZoneOffset.UTC).atTime(LocalTime.of(2, 0)).toInstant(ZoneOffset.UTC);
            return now.isBefore(todayRun) ? todayRun : todayRun.plus(Duration.ofDays(1));
        }
        Instant base = last != null ? last.getStartedAt() : now;
        Instant next = base.plusSeconds(d.intervalSeconds());
        return next.isBefore(now) ? now.plusSeconds(d.intervalSeconds()) : next;
    }

    private JobRunResponse toRunResponse(JobRun r) {
        return new JobRunResponse(r.getStartedAt(), r.isOk(), r.getDurationMs(), r.getMessage(), r.getTriggeredBy());
    }

    /** jobId → [runs, failed] over the last 24h. */
    private Map<String, long[]> counts24h() {
        Instant since = Instant.now().minus(Duration.ofHours(24));
        return jobRunRepository.countsSince(since).stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> new long[]{((Number) row[1]).longValue(),
                        row[2] != null ? ((Number) row[2]).longValue() : 0L}));
    }

    private long approvalPending() { return countStatus(ApprovalStatus.PENDING); }
    private long delayedPending() {
        try { return delayedEmailRepository.countByProcessedFalse(); } catch (Exception e) { return 0; }
    }
    private long countStatus(ApprovalStatus s) {
        try { return pendingActionRepository.countByStatus(s); } catch (Exception e) { return 0; }
    }

    private JobDef requireDef(String id) {
        JobDef d = def(id);
        if (d == null) throw new ResourceNotFoundException("Job", id);
        return d;
    }
}
