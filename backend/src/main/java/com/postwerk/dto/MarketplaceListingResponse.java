package com.postwerk.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Card + base detail representation of a marketplace listing.
 *
 * @param owned whether the requesting user already has an active acquisition of this listing
 */
public record MarketplaceListingResponse(
        UUID id,
        String name,
        String tagline,
        String description,
        String category,
        String kind,
        String visibility,
        String pricingModel,
        BigDecimal price,
        String version,
        String icon,
        String color,
        String ioInIcon,
        String ioInLabel,
        String ioOutIcon,
        String ioOutLabel,
        int nodeCount,
        int constantCount,
        BigDecimal ratingAvg,
        int ratingCount,
        int installCount,
        boolean featured,
        boolean verified,
        String status,
        AuthorSummaryDto author,
        boolean owned,
        Instant createdAt,
        Instant updatedAt
) {}
