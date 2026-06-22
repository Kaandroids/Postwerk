package com.postwerk.dto.admin;

import java.util.UUID;

/** Response DTO for per-user AI token usage breakdown. */
public record AiUsageByUserResponse(
        UUID userId,
        String email,
        String fullName,
        long promptTokens,
        long outputTokens,
        long totalTokens,
        long requestCount,
        int costCents
) {}
