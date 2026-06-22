package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full mailbox detail for the Email Health detail modal: the list-row fields plus connection config
 * (IMAP/SMTP host/port/TLS — never credentials), sync status, the last error, and the recent-attempts
 * timeline. Backs the Overview + Recent-sync-attempts tabs.
 */
public record AdminMailboxHealthDetailResponse(
        UUID id,
        String email,
        String displayName,
        String color,
        UUID ownerOrgId,
        String ownerOrgName,
        String ownerEmail,
        List<String> protocols,
        String health,
        boolean paused,
        // Connection config (safe — no usernames/passwords)
        String imapHost,
        Integer imapPort,
        Boolean imapSsl,
        boolean readEnabled,
        String smtpHost,
        Integer smtpPort,
        Boolean smtpSsl,
        boolean sendEnabled,
        // Sync status
        String server,
        Instant lastSyncAt,
        Long syncAgoMinutes,
        boolean stale,
        String lastError,
        Instant lastErrorAt,
        Integer queueDepth,
        Instant createdAt,
        List<MailboxSyncAttemptResponse> recentAttempts
) {}
