package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One mailbox row in the platform-staff Email Health list. Carries only safe metadata + derived
 * connection-health fields — never the encrypted IMAP/SMTP credentials.
 *
 * <p>{@code health} is derived from the persisted {@code lastSyncStatus}: {@code ok} / {@code failing}
 * (connection/TLS error) / {@code auth_error} (credentials rejected); a never-synced mailbox is
 * reported {@code ok} (pending its first sync). {@code server} is the IMAP host (the de-facto relay
 * "cluster"). {@code queueDepth} is always null — outbound send is synchronous, there is no queue.</p>
 */
public record AdminMailboxHealthResponse(
        UUID id,
        String email,
        String displayName,
        String color,
        UUID ownerOrgId,
        String ownerOrgName,
        String ownerEmail,
        List<String> protocols,      // subset of ["IMAP","SMTP"]
        String health,               // ok | failing | auth_error
        boolean paused,
        Instant lastSyncAt,
        Long syncAgoMinutes,         // null = never synced
        boolean stale,               // syncAgoMinutes > 1440 (24h)
        String lastError,            // null when healthy
        String server,               // IMAP host = relay cluster
        Integer queueDepth,          // always null (no send queue)
        boolean imapConfigured,
        boolean smtpConfigured
) {}
