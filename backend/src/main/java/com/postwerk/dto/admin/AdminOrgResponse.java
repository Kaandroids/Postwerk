package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for an organization row in the admin organization list. */
public record AdminOrgResponse(
        UUID id,
        String name,
        String slug,
        boolean personal,
        UUID ownerUserId,
        String ownerEmail,
        String ownerName,
        String planName,
        long memberCount,
        Instant createdAt,
        Instant suspendedAt
) {}
