package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for a classification category. */
public record CategoryResponse(
        UUID id,
        String name,
        String color,
        String description,
        String positiveExample,
        String negativeExample,
        boolean locked,
        Instant createdAt
) {}
