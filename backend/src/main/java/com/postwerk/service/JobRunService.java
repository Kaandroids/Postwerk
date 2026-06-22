package com.postwerk.service;

import com.postwerk.model.JobRun;
import com.postwerk.repository.JobRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Records each execution of a recurring platform job and enforces staff schedule-pauses, wrapped
 * around the real {@code @Scheduled} methods (admin Background Jobs).
 *
 * <p>Recording is best-effort and isolated in its OWN transaction (REQUIRES_NEW), so it persists even
 * when the wrapped job rolls back, and a recording failure can never break the job. {@link #run} skips
 * paused jobs and re-throws the job's own exceptions (preserving the original fire-and-forget / tx
 * behaviour). Pause flags live in Redis ({@code job:paused:<id>}).</p>
 *
 * @since 1.0
 */
@Service
public class JobRunService {

    private static final Logger log = LoggerFactory.getLogger(JobRunService.class);
    private static final String PAUSE_KEY = "job:paused:";

    private final JobRunRepository jobRunRepository;
    private final StringRedisTemplate redis;
    private final TransactionTemplate requiresNew;

    public JobRunService(JobRunRepository jobRunRepository, StringRedisTemplate redis,
                         PlatformTransactionManager txManager) {
        this.jobRunRepository = jobRunRepository;
        this.redis = redis;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Wraps a scheduled job: skips if paused, times it, records the run (best-effort), and re-throws. */
    public void run(String jobId, Runnable work) {
        if (isPaused(jobId)) {
            log.debug("Job {} is paused (staff), skipping run", jobId);
            return;
        }
        record(jobId, work, "schedule", null);
    }

    /** Runs a job on demand (staff "run now"), recording it as a manual run. Never throws. */
    public void runManual(String jobId, Runnable work, UUID actorUserId) {
        try {
            record(jobId, work, "manual", actorUserId);
        } catch (RuntimeException e) {
            log.warn("Manual run of job {} failed: {}", jobId, e.getMessage());
        }
    }

    private void record(String jobId, Runnable work, String triggeredBy, UUID actorUserId) {
        Instant start = Instant.now();
        long startMs = System.currentTimeMillis();
        boolean ok = true;
        String message = null;
        RuntimeException thrown = null;
        try {
            work.run();
        } catch (RuntimeException e) {
            ok = false;
            message = e.getMessage();
            thrown = e;
        }
        long durationMs = System.currentTimeMillis() - startMs;
        save(jobId, start, durationMs, ok, message, triggeredBy, actorUserId);
        if (thrown != null) throw thrown;
    }

    /** Persists a run record in its own transaction; swallows any recording error. */
    private void save(String jobId, Instant start, long durationMs, boolean ok, String message,
                      String triggeredBy, UUID actorUserId) {
        try {
            requiresNew.executeWithoutResult(status -> jobRunRepository.save(JobRun.builder()
                    .jobId(jobId)
                    .startedAt(start)
                    .finishedAt(start.plusMillis(durationMs))
                    .ok(ok)
                    .durationMs(durationMs)
                    .message(message != null && message.length() > 1000 ? message.substring(0, 1000) : message)
                    .triggeredBy(triggeredBy)
                    .actorUserId(actorUserId)
                    .build()));
        } catch (Exception e) {
            log.warn("Failed to record job run for {}: {}", jobId, e.getMessage());
        }
    }

    public boolean isPaused(String jobId) {
        try {
            return "true".equals(redis.opsForValue().get(PAUSE_KEY + jobId));
        } catch (Exception e) {
            return false; // Redis down → never block the job.
        }
    }

    public void setPaused(String jobId, boolean paused) {
        if (paused) {
            redis.opsForValue().set(PAUSE_KEY + jobId, "true");
        } else {
            redis.delete(PAUSE_KEY + jobId);
        }
    }
}
