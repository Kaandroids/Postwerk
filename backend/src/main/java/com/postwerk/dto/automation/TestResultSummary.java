package com.postwerk.dto.automation;

import java.time.Instant;

/** Summary DTO for an automation test run result. */
public record TestResultSummary(
        String status,
        int passedCount,
        int totalCount,
        long durationMs,
        Instant executedAt
) {}
