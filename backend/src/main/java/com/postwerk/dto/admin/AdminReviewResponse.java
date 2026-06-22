package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/** One marketplace review row in the admin Marketplace Moderation list / listing-detail Reviews tab. */
public record AdminReviewResponse(
        UUID id,
        UUID listingId,
        String listingName,
        String authorName,
        String authorEmail,
        int rating,
        String text,
        boolean hidden,
        Instant createdAt
) {}
