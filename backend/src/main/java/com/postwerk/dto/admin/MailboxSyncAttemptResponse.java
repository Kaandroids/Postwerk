package com.postwerk.dto.admin;

import java.time.Instant;

/**
 * A single sync attempt in a mailbox's recent-attempts timeline (Email Health detail).
 *
 * <p>The platform persists only the latest attempt's outcome on the mailbox row (no per-attempt
 * history table yet), so this list currently holds at most one entry — the most recent attempt.</p>
 */
public record MailboxSyncAttemptResponse(
        Instant at,
        boolean ok,
        String message
) {}
