package com.postwerk.dto.analytics;

import com.postwerk.dto.analytics.AnalyticsOverviewResponse.TrendPoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Per-automation analytics drill-down (Screen 2): the same time window applied to a single automation —
 * its execution trend, per-node failure rates, and the last runs. Mirrors the {@code AutomationAnalyticsDetail}
 * shape the frontend binds to. Simulation (TESTING) traces are excluded.
 *
 * <p>AI cost is never shown to end users as a money amount. AI usage is expressed only as this
 * automation's share of the org's AI usage in the window ({@link Kpis#aiSharePct()}), derived from
 * its share of runs (token usage is not linked to a flow).</p>
 *
 * @since 1.0
 */
public record AutomationAnalyticsResponse(
        AutomationInfo automation,
        String range,
        int days,
        Kpis kpis,
        List<TrendPoint> trend,
        List<NodeFailure> nodeFailures,
        List<RecentRun> recentRuns
) {

    public record AutomationInfo(UUID id, String name, String color, String status, String kind, Instant lastRunAt) {}

    public record Kpis(long runs, double successRate, long failedRuns, double failRate,
                       long emailsProcessed, double processedPct,
                       Double aiSharePct,
                       List<Long> runsSeries, List<Long> failsSeries) {}

    /** Failure rate of one node within this automation's flow. */
    public record NodeFailure(UUID nodeId, String nodeLabel, String nodeType, long failures, long total, double failRate) {}

    /** A recent live run (trace) of this automation. {@code durationMs} is null while still running. */
    public record RecentRun(UUID traceId, String status, Instant startedAt, Long durationMs,
                            String emailSubject, String emailFrom, String errorMessage) {}
}
