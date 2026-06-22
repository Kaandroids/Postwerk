package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** An organization a user is a member of, for the admin user-detail "Organizations" tab. */
public record AdminUserOrgResponse(
        UUID orgId,
        String name,
        String slug,
        boolean personal,
        String role,
        String status,
        Instant joinedAt
) {}
