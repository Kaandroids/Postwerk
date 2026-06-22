package com.postwerk.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A buyer's acquisition (entitlement) joined with its listing summary and installed-copy state.
 *
 * @param installedAutomationId the buyer-owned installed automation copy
 * @param hidden                whether the installed copy is content-hidden (PRIVATE listing)
 * @param installedStatus       status of the installed automation (PAUSED/ACTIVE/...)
 */
public record MarketplaceAcquisitionResponse(
        UUID id,
        UUID listingId,
        UUID installedAutomationId,
        String pricingModel,
        BigDecimal price,
        String status,
        boolean hidden,
        String installedStatus,
        MarketplaceListingResponse listing,
        Instant createdAt
) {}
