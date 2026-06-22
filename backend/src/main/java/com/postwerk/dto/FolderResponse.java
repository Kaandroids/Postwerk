package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for an IMAP folder with message counts. */
public record FolderResponse(
        UUID id,
        String name,
        String role,
        int messageCount,
        int unreadCount,
        Instant lastSyncedAt
) {}
