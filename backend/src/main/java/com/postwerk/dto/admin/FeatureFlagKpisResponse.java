package com.postwerk.dto.admin;

/**
 * KPI roll-up for the Feature Flags console — definition / state / staleness counts only
 * (no exposure analytics). {@code inFlight} == {@code partial} (rolling out).
 *
 * @since 1.0
 */
public record FeatureFlagKpisResponse(
        long total,
        long on,
        long partial,
        long off,
        long killed,
        long archived,
        long stale,
        long inFlight
) {}
