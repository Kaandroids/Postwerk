package com.postwerk.dto;

import java.time.Instant;

/** Response DTO for an IMAP email sync operation result. */
public record EmailSyncResponse(
    int newEmailCount,
    Instant syncedAt
) {}
