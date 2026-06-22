package com.postwerk.event;

import java.util.UUID;

/**
 * Published when a live automation run finishes with status FAILED. A notification listener turns it
 * into an {@code AUTOMATION_FAILED} notification for the automation owner + the org's active
 * owners/admins (hourly-deduped). See {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
public record AutomationFailedEvent(UUID organizationId,
                                    UUID ownerUserId,
                                    UUID automationId,
                                    String automationName,
                                    String errorMessage) {
}
