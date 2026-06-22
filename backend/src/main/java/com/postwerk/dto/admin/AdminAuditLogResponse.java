package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for admin-facing audit log entries with user context. */
public record AdminAuditLogResponse(
        UUID id,
        UUID userId,
        String userEmail,
        String userName,
        String action,
        String detail,
        String ipAddress,
        Instant createdAt
) {}
