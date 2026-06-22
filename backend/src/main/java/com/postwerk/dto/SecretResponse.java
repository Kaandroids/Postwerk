package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for a secret (value is never exposed). */
public record SecretResponse(
        UUID id,
        String name,
        String description,
        int version,
        Instant lastRotatedAt,
        Instant createdAt,
        Instant updatedAt
) {}
