package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for a user entry in the admin user management view. */
public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        String company,
        String role,
        String staffRole,
        Instant lastLoginAt,
        String lastLoginIp,
        Instant createdAt,
        boolean deleted,
        long emailAccountCount,
        long automationCount,
        long totalTokensUsed,
        /** Name of the user's effective plan (their personal org's plan), or {@code null} if none. */
        String planName,
        /** Number of active organization memberships the user holds. */
        long orgCount,
        /** AI cost the user has incurred since the start of this UTC month, in micros (1 USD = 1M micros). */
        long aiCostMicrosThisMonth,
        /** The user's plan AI cap in cents: {@code -1} unlimited, {@code 0} disabled, {@code >0} monthly cent cap. */
        long costLimitCents
) {}
