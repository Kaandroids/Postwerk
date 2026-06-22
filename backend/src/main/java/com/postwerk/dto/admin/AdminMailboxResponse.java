package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * An email account (mailbox) for the admin user/organization detail tabs. Never carries the
 * encrypted IMAP/SMTP passwords or any other secret — only the safe metadata the admin UI displays.
 *
 * <p>Field set is limited to what {@link com.postwerk.model.EmailAccount} actually exposes safely:
 * {@code id}, {@code email}, {@code displayName}, {@code color}, the active flag and {@code createdAt}.
 * There is no {@code provider} column on the entity, so it is omitted.</p>
 */
public record AdminMailboxResponse(
        UUID id,
        String email,
        String displayName,
        String color,
        boolean active,
        Instant createdAt
) {}
