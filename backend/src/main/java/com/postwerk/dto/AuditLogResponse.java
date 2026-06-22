package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for user-facing audit log entries. */
public record AuditLogResponse(
        UUID id,
        UUID userId,
        String userName,
        String action,
        String detail,
        String ipAddress,
        Instant createdAt
) {}
