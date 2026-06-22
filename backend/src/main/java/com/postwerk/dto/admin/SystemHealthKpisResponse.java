package com.postwerk.dto.admin;

/**
 * KPI strip totals for the admin System Health header, derived from live subsystem probes.
 *
 * <p>Honest sourcing: {@code apiLatencyMs}/{@code errorRatePct}/{@code requestsPerMin} come from
 * Micrometer {@code http.server.requests} (latency is the cumulative <em>mean</em>, not p95 — no
 * percentile histogram is published; rpm is the average since process start). Null/zero where a
 * source is unavailable.</p>
 */
public record SystemHealthKpisResponse(
        Long apiLatencyMs,
        Double errorRatePct,
        Long requestsPerMin,
        Integer dbPoolUsed,
        Integer dbPoolMax,
        Long redisMemUsedMb,
        Long redisMemMaxMb,
        Long jobQueueDepth,
        long down,
        long degraded,
        long ok,
        long total,
        long uptimeMs
) {}
