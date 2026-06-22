package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/** A single review on a listing, including the reviewer's display name. */
public record ReviewResponse(
        UUID id,
        UUID userId,
        String userName,
        int rating,
        String text,
        Instant createdAt,
        boolean mine
) {}
