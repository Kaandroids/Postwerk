package com.postwerk.event;

import java.util.UUID;

/**
 * Published after an AI usage row is recorded for an organization. A notification listener checks the
 * month's spend against the org's cost cap and emits a {@code QUOTA_WARNING} (≥80%) or
 * {@code QUOTA_EXCEEDED} (≥100%) notification, deduped once per org per billing period. Carrying only
 * the org id keeps {@code AiUsageService} decoupled from quota/notification logic. See
 * {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
public record AiUsageRecordedEvent(UUID organizationId) {
}
