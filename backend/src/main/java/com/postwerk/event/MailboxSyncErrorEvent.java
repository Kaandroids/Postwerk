package com.postwerk.event;

import java.util.UUID;

/**
 * Published when an email account's sync transitions from healthy to failing (so it fires once per
 * breakage, not every 5-minute poll). A notification listener emits a {@code MAILBOX_AUTH_ERROR}
 * (bad credentials/token) or {@code MAILBOX_CONN_ERROR} (connection) notification for the account
 * owner + the org's active owners/admins. See {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
public record MailboxSyncErrorEvent(UUID organizationId,
                                    UUID ownerUserId,
                                    UUID accountId,
                                    String accountEmail,
                                    boolean authError) {
}
