package com.postwerk.service.impl;

import com.postwerk.dto.analytics.AnalyticsOverviewResponse;
import com.postwerk.dto.analytics.AnalyticsOverviewResponse.AiCost;
import com.postwerk.dto.analytics.AnalyticsOverviewResponse.ApprovalStats;
import com.postwerk.dto.analytics.AnalyticsOverviewResponse.CostSlice;
import com.postwerk.dto.analytics.AnalyticsOverviewResponse.DailyCost;
import com.postwerk.dto.analytics.AnalyticsOverviewResponse.Deltas;
import com.postwerk.dto.analytics.AnalyticsOverviewResponse.FailureRow;
import com.postwerk.dto.analytics.AnalyticsOverviewResponse.Kpis;
import com.postwerk.dto.analytics.AnalyticsOverviewResponse.TopAutomation;
import com.postwerk.dto.analytics.AnalyticsOverviewResponse.TrendPoint;
import com.postwerk.dto.analytics.AutomationAnalyticsResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Automation;
import com.postwerk.model.Email;
import com.postwerk.model.EmailAutomationTrace;
import com.postwerk.model.enums.ApprovalStatus;
import com.postwerk.model.enums.AutomationStatus;
import com.postwerk.repository.AiTokenUsageRepository;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.EmailAutomationTraceRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.repository.PendingActionRepository;
import com.postwerk.service.AnalyticsService;
import com.postwerk.service.QuotaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.postwerk.util.MonetaryConstants.MICROS_PER_CENT;

/**
 * Default {@link AnalyticsService}. Builds the overview + drill-down from native trace/usage
 * aggregations, filling a fixed daily axis (zeros for quiet days) so charts render a continuous range.
 *
 * @since 1.0
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final ZoneOffset ZONE = ZoneOffset.UTC;

    private final AutomationRepository automationRepository;
    private final EmailAutomationTraceRepository traceRepository;
    private final AiTokenUsageRepository aiUsageRepository;
    private final PendingActionRepository pendingActionRepository;
    private final EmailRepository emailRepository;
    private final QuotaService quotaService;

    public AnalyticsServiceImpl(AutomationRepository automationRepository,
                                EmailAutomationTraceRepository traceRepository,
                                AiTokenUsageRepository aiUsageRepository,
                                PendingActionRepository pendingActionRepository,
                                EmailRepository emailRepository,
                                QuotaService quotaService) {
        this.automationRepository = automationRepository;
        this.traceRepository = traceRepository;
        this.aiUsageRepository = aiUsageRepository;
        this.pendingActionRepository = pendingActionRepository;
        this.emailRepository = emailRepository;
        this.quotaService = quotaService;
    }

    // ── overview ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverview(UUID organizationId, String range) {
        int days = parseDays(range);
        List<LocalDate> axis = dateAxis(days);
        Instant since = axis.get(0).atStartOfDay(ZONE).toInstant();

        List<Automation> automations = automationRepository.findByOrganizationId(organizationId);
        List<UUID> automationIds = automations.stream().map(Automation::getId).toList();
        int activeAutomations = (int) automations.stream()
                .filter(a -> a.getStatus() == AutomationStatus.ACTIVE).count();

        // execution trend (zero-filled axis)
        List<TrendPoint> trend = buildTrend(axis, automationIds.isEmpty()
                ? List.of()
                : traceRepository.dailyStatusCounts(automationIds, since));
        long totalRuns = trend.stream().mapToLong(TrendPoint::total).sum();
        long failedRuns = trend.stream().mapToLong(TrendPoint::failed).sum();
        List<Long> runsSeries = trend.stream().map(TrendPoint::total).toList();
        List<Long> failsSeries = trend.stream().map(TrendPoint::failed).toList();

        // emails processed (of incoming)
        long emailsProcessed = automationIds.isEmpty() ? 0
                : traceRepository.countDistinctEmails(automationIds, since);
        long incoming = emailRepository.countByOrganizationIdSince(organizationId, since);

        // AI cost
        long aiCostCents = centsFromMicros(aiUsageRepository.sumCostMicrosByOrganizationSince(organizationId, since));
        AiCost aiCost = buildAiCost(organizationId, since, axis, aiCostCents);
        List<Long> costSeries = aiCost.dailyCents().stream().map(DailyCost::cents).toList();
        Long costCapCents = resolveCostCap(organizationId);

        // top automations
        List<TopAutomation> topAutomations = automationIds.isEmpty() ? List.of()
                : buildTopAutomations(automations, traceRepository.automationStats(automationIds, since));

        // failure analysis by node type
        List<FailureRow> failureAnalysis = automationIds.isEmpty() ? List.of()
                : buildFailureRows(traceRepository.failuresByNodeType(automationIds, since));

        // approvals throughput
        ApprovalStats approvals = buildApprovals(organizationId, since);

        // deltas vs. previous equal window
        Deltas deltas = buildDeltas(organizationId, automationIds, since, days, totalRuns, aiCostCents);

        Kpis kpis = new Kpis(
                totalRuns, runsSeries,
                pct(totalRuns - failedRuns, totalRuns),
                emailsProcessed, Math.min(100.0, pct(emailsProcessed, incoming)),
                failedRuns, pct(failedRuns, totalRuns), failsSeries,
                aiCostCents, costSeries, costCapCents,
                approvals.pending(), approvals.avgDecisionMinutes(),
                deltas);

        return new AnalyticsOverviewResponse(range(range), days, activeAutomations,
                kpis, trend, aiCost, topAutomations, failureAnalysis, approvals);
    }

    // ── automation detail ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AutomationAnalyticsResponse getAutomationDetail(UUID organizationId, UUID automationId, String range) {
        Automation auto = automationRepository.findByIdAndOrganizationId(automationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Automation", automationId));

        int days = parseDays(range);
        List<LocalDate> axis = dateAxis(days);
        Instant since = axis.get(0).atStartOfDay(ZONE).toInstant();

        List<TrendPoint> trend = buildTrend(axis, traceRepository.dailyStatusCountsForAutomation(automationId, since));
        long runs = trend.stream().mapToLong(TrendPoint::total).sum();
        long failed = trend.stream().mapToLong(TrendPoint::failed).sum();
        long emailsProcessed = traceRepository.countDistinctEmailsForAutomation(automationId, since);

        // AI cost is never shown to users as money. Express this automation's AI footprint as its
        // share of the org's runs in the window (a money-free proxy, since usage isn't flow-linked).
        List<UUID> orgIds = automationRepository.findByOrganizationId(organizationId)
                .stream().map(Automation::getId).toList();
        long orgRuns = orgIds.isEmpty() ? 0 : traceRepository.countRunsBetween(orgIds, since, Instant.now());
        Double aiSharePct = orgRuns > 0 ? pct(runs, orgRuns) : null;

        List<AutomationAnalyticsResponse.NodeFailure> nodeFailures =
                buildNodeFailures(traceRepository.nodeFailuresForAutomation(automationId, since));

        List<AutomationAnalyticsResponse.RecentRun> recentRuns =
                traceRepository.findByAutomationIdAndSimulationFalseOrderByStartedAtDesc(automationId, PageRequest.of(0, 10))
                        .map(this::toRecentRun).getContent();

        AutomationAnalyticsResponse.Kpis kpis = new AutomationAnalyticsResponse.Kpis(
                runs, pct(runs - failed, runs), failed, pct(failed, runs),
                emailsProcessed, Math.min(100.0, pct(emailsProcessed, runs)),
                aiSharePct,
                trend.stream().map(TrendPoint::total).toList(),
                trend.stream().map(TrendPoint::failed).toList());

        AutomationAnalyticsResponse.AutomationInfo info = new AutomationAnalyticsResponse.AutomationInfo(
                auto.getId(), auto.getName(), auto.getColor(),
                auto.getStatus() != null ? auto.getStatus().name() : null,
                auto.getKind() != null ? auto.getKind().name() : null,
                auto.getLastRunAt());

        return new AutomationAnalyticsResponse(info, range(range), days, kpis, trend, nodeFailures, recentRuns);
    }

    // ── builders ───────────────────────────────────────────────────────

    /** Fold daily [date, status, count] rows onto the axis; total = all statuses, success = total − failed. */
    private List<TrendPoint> buildTrend(List<LocalDate> axis, List<Object[]> rows) {
        Map<LocalDate, long[]> byDate = new HashMap<>(); // [total, failed]
        for (Object[] r : rows) {
            LocalDate d = asDate(r[0]);
            String status = (String) r[1];
            long count = asLong(r[2]);
            long[] agg = byDate.computeIfAbsent(d, k -> new long[2]);
            agg[0] += count;
            if ("FAILED".equals(status)) agg[1] += count;
        }
        List<TrendPoint> out = new ArrayList<>(axis.size());
        for (LocalDate d : axis) {
            long[] agg = byDate.getOrDefault(d, new long[2]);
            long total = agg[0];
            long failed = agg[1];
            out.add(new TrendPoint(d, total, total - failed, failed));
        }
        return out;
    }

    private AiCost buildAiCost(UUID org, Instant since, List<LocalDate> axis, long totalCents) {
        List<CostSlice> byOperation = toSlices(aiUsageRepository.costByOperationSince(org, since));
        List<CostSlice> byModel = toSlices(aiUsageRepository.costByModelSince(org, since));

        Map<LocalDate, Long> dailyMicros = new HashMap<>();
        for (Object[] r : aiUsageRepository.dailyCostMicros(org, since)) {
            dailyMicros.put(asDate(r[0]), asLong(r[1]));
        }
        List<DailyCost> dailyCents = new ArrayList<>(axis.size());
        for (LocalDate d : axis) {
            dailyCents.add(new DailyCost(d, centsFromMicros(dailyMicros.getOrDefault(d, 0L))));
        }
        return new AiCost(totalCents, byOperation, byModel, dailyCents);
    }

    private List<CostSlice> toSlices(List<Object[]> rows) {
        List<CostSlice> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            long cents = centsFromMicros(asLong(r[1]));
            if (cents <= 0) continue; // drop empty slices from the donut
            out.add(new CostSlice((String) r[0], cents, asLong(r[2])));
        }
        return out;
    }

    private List<TopAutomation> buildTopAutomations(List<Automation> automations, List<Object[]> stats) {
        Map<UUID, long[]> byId = new HashMap<>();   // [total, failed]
        Map<UUID, Instant> lastRun = new HashMap<>();
        for (Object[] r : stats) {
            UUID id = asUuid(r[0]);
            byId.put(id, new long[]{asLong(r[1]), asLong(r[2])});
            lastRun.put(id, asInstant(r[3]));
        }
        List<TopAutomation> out = new ArrayList<>();
        for (Automation a : automations) {
            long[] agg = byId.get(a.getId());
            if (agg == null || agg[0] == 0) continue;
            long runs = agg[0], failed = agg[1];
            out.add(new TopAutomation(a.getId(), a.getName(), a.getColor(), runs,
                    pct(runs - failed, runs), failed, lastRun.get(a.getId())));
        }
        out.sort(Comparator.comparingLong(TopAutomation::runs).reversed());
        return out.size() > 10 ? out.subList(0, 10) : out;
    }

    private List<FailureRow> buildFailureRows(List<Object[]> rows) {
        List<FailureRow> out = new ArrayList<>();
        for (Object[] r : rows) {
            long total = asLong(r[2]);
            if (total == 0) continue;
            long failures = asLong(r[1]);
            out.add(new FailureRow((String) r[0], failures, total, pct(failures, total)));
        }
        out.sort(Comparator.comparingDouble(FailureRow::failRate).reversed());
        return out;
    }

    private List<AutomationAnalyticsResponse.NodeFailure> buildNodeFailures(List<Object[]> rows) {
        List<AutomationAnalyticsResponse.NodeFailure> out = new ArrayList<>();
        for (Object[] r : rows) {
            long total = asLong(r[4]);
            long failures = asLong(r[3]);
            out.add(new AutomationAnalyticsResponse.NodeFailure(
                    asUuid(r[0]), (String) r[1], (String) r[2], failures, total, pct(failures, total)));
        }
        out.sort(Comparator.comparingDouble(AutomationAnalyticsResponse.NodeFailure::failRate).reversed());
        return out;
    }

    private ApprovalStats buildApprovals(UUID org, Instant since) {
        long pending = pendingActionRepository.countByOrganizationIdAndStatus(org, ApprovalStatus.PENDING);
        long approved = pendingActionRepository.countByOrganizationIdAndStatusAndDecidedAtAfter(org, ApprovalStatus.APPROVED, since);
        long rejected = pendingActionRepository.countByOrganizationIdAndStatusAndDecidedAtAfter(org, ApprovalStatus.REJECTED, since);
        long expired = pendingActionRepository.countByOrganizationIdAndStatusAndDecidedAtAfter(org, ApprovalStatus.EXPIRED, since);
        Double avg = pendingActionRepository.avgDecisionMinutesSince(org, since);
        return new ApprovalStats(pending, approved, rejected, expired, avg == null ? null : Math.round(avg));
    }

    private Deltas buildDeltas(UUID org, List<UUID> automationIds, Instant since, int days,
                               long curRuns, long curCostCents) {
        Instant prevFrom = since.minus(Duration.ofDays(days));
        long prevRuns = automationIds.isEmpty() ? 0 : traceRepository.countRunsBetween(automationIds, prevFrom, since);
        long prevCostCents = centsFromMicros(aiUsageRepository.sumCostMicrosByOrganizationBetween(org, prevFrom, since));
        return new Deltas(deltaPct(curRuns, prevRuns), deltaPct(curCostCents, prevCostCents));
    }

    private AutomationAnalyticsResponse.RecentRun toRecentRun(EmailAutomationTrace t) {
        Email email = t.getEmail();
        Long durationMs = (t.getStartedAt() != null && t.getCompletedAt() != null)
                ? Duration.between(t.getStartedAt(), t.getCompletedAt()).toMillis() : null;
        return new AutomationAnalyticsResponse.RecentRun(
                t.getId(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getStartedAt(), durationMs,
                email != null ? email.getSubject() : null,
                email != null ? email.getFromAddress() : null,
                t.getErrorMessage());
    }

    private Long resolveCostCap(UUID org) {
        int limit = quotaService.getUsage(org).plan().costLimitCents();
        return limit < 0 ? null : (long) limit; // -1 = unlimited → no cap line
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static int parseDays(String range) {
        if (range == null) return 30;
        return switch (range) {
            case "7d" -> 7;
            case "90d" -> 90;
            default -> 30;
        };
    }

    private static String range(String range) {
        return switch (range == null ? "" : range) {
            case "7d" -> "7d";
            case "90d" -> "90d";
            default -> "30d";
        };
    }

    /** Inclusive list of the last {@code days} calendar dates (UTC), oldest first. */
    private static List<LocalDate> dateAxis(int days) {
        LocalDate today = LocalDate.now(ZONE);
        List<LocalDate> axis = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) axis.add(today.minusDays(i));
        return axis;
    }

    private static double pct(long n, long d) {
        return d > 0 ? Math.round((double) n / d * 1000.0) / 10.0 : 0.0;
    }

    /** Percent change vs. previous window; null when there is no prior baseline. */
    private static Double deltaPct(long cur, long prev) {
        if (prev <= 0) return null;
        return Math.round((double) (cur - prev) / prev * 1000.0) / 10.0;
    }

    private static long centsFromMicros(long micros) {
        return Math.round((double) micros / MICROS_PER_CENT);
    }

    private static long asLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static UUID asUuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(String.valueOf(o));
    }

    private static LocalDate asDate(Object o) {
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.util.Date d) return d.toInstant().atZone(ZONE).toLocalDate();
        return LocalDate.parse(String.valueOf(o));
    }

    private static Instant asInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof java.util.Date d) return d.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(String.valueOf(o));
    }
}
