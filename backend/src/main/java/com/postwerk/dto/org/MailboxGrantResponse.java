package com.postwerk.dto.org;

import java.util.UUID;

/**
 * A member's access to one organization mailbox (#4) — one entry per org mailbox, with the
 * member's current read/send flags (false when no grant exists).
 *
 * @since 1.0
 */
public record MailboxGrantResponse(
        UUID mailboxId,
        String email,
        boolean canRead,
        boolean canSend) {
}
