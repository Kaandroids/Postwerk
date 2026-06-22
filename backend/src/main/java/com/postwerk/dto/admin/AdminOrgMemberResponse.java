package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** A member row inside the admin organization detail view. */
public record AdminOrgMemberResponse(
        UUID userId,
        String email,
        String fullName,
        String role,
        String status,
        Instant joinedAt
) {}
