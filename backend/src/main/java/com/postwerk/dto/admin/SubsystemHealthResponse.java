package com.postwerk.dto.admin;

import java.util.List;
import java.util.Map;

/**
 * One platform subsystem's live health for the admin System Health screen (card + detail modal).
 *
 * <p>{@code status}: {@code ok} | {@code degraded} | {@code down}. {@code metrics} is a per-kind
 * ordered map of label→value (the detail modal renders them as Field rows). {@code primary} is the
 * one-line card metric. Fields with no real telemetry source are simply omitted from {@code metrics}
 * rather than faked. {@code lastCheckedMinutes} is ~0 (probed live per request).</p>
 */
public record SubsystemHealthResponse(
        String id,
        String name,
        String kind,
        String version,
        String status,
        String primary,
        Map<String, Object> metrics,
        Long lastCheckedMinutes,
        String lastError,
        List<SubsystemCheckResponse> recentChecks
) {}
