package com.postwerk.dto.admin;

import java.time.Instant;

/** One execution in a job's recent-runs timeline (admin Background Jobs). */
public record JobRunResponse(
        Instant at,
        boolean ok,
        long durationMs,
        String message,
        String triggeredBy
) {}
