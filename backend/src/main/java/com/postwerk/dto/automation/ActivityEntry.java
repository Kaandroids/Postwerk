package com.postwerk.dto.automation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One live automation run for the production activity feed (#3d): what the automation did to an
 * incoming email, including each step's result and the AI's reasoning.
 *
 * @since 1.0
 */
public record ActivityEntry(
        UUID traceId,
        UUID automationId,
        String automationName,
        String automationColor,
        String emailSubject,
        String emailFrom,
        String status,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        List<ActivityStep> steps
) {
    /** A single node's outcome in the run, with a human-readable summary of its AI reasoning / action. */
    public record ActivityStep(
            String nodeType,
            String nodeLabel,
            String resultStatus,
            String summary
    ) {}
}
