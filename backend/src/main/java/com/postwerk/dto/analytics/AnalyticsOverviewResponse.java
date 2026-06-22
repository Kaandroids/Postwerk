package com.postwerk.dto.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Org-wide analytics overview (Screen 1) for the user dashboard Analytics page. Aggregates live
 * execution traces, AI cost, and approval throughput over a {@code 7d / 30d / 90d} window. Mirrors the
 * {@code AnalyticsOverview} shape the frontend binds to. Simulation (TESTING) traces are excluded.
 *
 * @since 1.0
 */
public record AnalyticsOverviewResponse(
        String range,
        int days,
        int activeAutomations,
        Kpis kpis,
        List<TrendPoint> executionTrend,
        AiCost aiCost,
        List<TopAutomation> topAutomations,
        List<FailureRow> failureAnalysis,
        ApprovalStats approvals
) {

    /** Headline numbers for the KPI strip, with daily sparkline series and period-over-period deltas. */
    public record Kpis(
            long totalRuns,
            List<Long> runsSeries,
            double successRate,
            long emailsProcessed,
            double processedPct,
            long failedRuns,
            double failRate,
            List<Long> failsSeries,
            long aiCostCents,
            List<Long> costSeries,
            Long costCapCents,
            long pendingApprovals,
            Long avgDecisionMinutes,
            Deltas deltas
    ) {}

    /** Percent change vs. the immediately preceding equal-length window. {@code null} = no prior data. */
    public record Deltas(Double runs, Double cost) {}

    /** One daily bucket of the execution trend. {@code total = success + failed} (running folded into success). */
    public record TrendPoint(LocalDate date, long total, long success, long failed) {}

    /** AI cost breakdown for the window (cents), by operation + by model, plus a daily cost series. */
    public record AiCost(long totalCents, List<CostSlice> byOperation, List<CostSlice> byModel, List<DailyCost> dailyCents) {}

    /** A single donut/legend slice. {@code key} is the operation (CLASSIFY/…) or model name. */
    public record CostSlice(String key, long costCents, long tokens) {}

    public record DailyCost(LocalDate date, long cents) {}

    public record TopAutomation(UUID id, String name, String color, long runs, double successRate,
                                long failedRuns, Instant lastRunAt) {}

    /** Failures aggregated by node type across the org (where automations break). */
    public record FailureRow(String nodeType, long failures, long total, double failRate) {}

    public record ApprovalStats(long pending, long approved, long rejected, long expired, Long avgDecisionMinutes) {}
}
