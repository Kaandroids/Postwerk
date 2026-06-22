package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for the authenticated user's profile and consent data. */
public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String company,
        String phone,
        Instant lastLoginAt,
        String lastLoginIp,
        UUID planId,
        String planName
) {}
