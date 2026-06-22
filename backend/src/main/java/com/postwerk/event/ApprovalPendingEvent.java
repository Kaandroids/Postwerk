package com.postwerk.event;

import java.util.UUID;

/**
 * Published when a supervised-mode action is parked for human approval ({@code PendingActionService.park}).
 * A {@code @TransactionalEventListener(AFTER_COMMIT)} turns it into an {@code APPROVAL_PENDING}
 * notification for the automation owner + the org's active owners/admins. Decoupled via Spring events
 * so the executor/pending-action path never depends on the notification subsystem.
 *
 * @since 1.0
 */
public record ApprovalPendingEvent(UUID organizationId,
                                   UUID ownerUserId,
                                   UUID automationId,
                                   UUID pendingActionId,
                                   String automationName,
                                   String nodeLabel) {
}
