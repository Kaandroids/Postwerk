package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * One platform-staff member in the admin Staff & Roles roster. {@code tier} is PRIVILEGED (can
 * mutate) or READ_ONLY; {@code self} flags the acting staffer's own row (cannot self-modify).
 *
 * @since 1.0
 */
public record StaffMemberResponse(
        UUID id,
        String name,
        String email,
        String role,
        String tier,
        int capabilityCount,
        Instant lastActiveAt,
        Instant staffSince,
        boolean self
) {}
