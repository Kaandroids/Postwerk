package com.postwerk.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One marketplace listing row in the admin Marketplace Moderation list.
 *
 * <p>{@code status} is the staff-facing moderation state derived from the entity's
 * status/visibility/taken-down: {@code PUBLIC} | {@code PRIVATE} | {@code PAUSED} | {@code TAKEN_DOWN}.
 * {@code slug} is derived from the name (listings have no stored slug).</p>
 */
public record AdminListingResponse(
        UUID id,
        String name,
        String slug,
        String authorName,
        String authorEmail,
        String kind,
        String pricingModel,
        BigDecimal price,
        String status,
        boolean featured,
        boolean takenDown,
        long installCount,
        BigDecimal ratingAvg,
        int ratingCount,
        String category,
        Instant createdAt
) {}
