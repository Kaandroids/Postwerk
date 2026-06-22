package com.postwerk.dto.automation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A supervised-mode action awaiting (or having received) a human decision — one row of the
 * approval inbox.
 *
 * @param actionDetail the resolved payload (rendered subject/body, recipient, folder, url, …) so the
 *                     UI can show exactly what will happen
 * @since 1.0
 */
public record PendingActionResponse(
        UUID id,
        UUID automationId,
        UUID emailId,
        UUID nodeId,
        String nodeType,
        String nodeLabel,
        Map<String, Object> actionDetail,
        String status,
        Instant createdAt,
        Instant decidedAt,
        String decisionNote,
        /** The AI categorization that drove this action, if any (#3c — enables "wrong category?" correction). */
        TriggerCategory triggerCategory
) {
    /** The upstream CATEGORIZE result captured in the context snapshot. */
    public record TriggerCategory(String id, String name, Double confidence) {}
}
