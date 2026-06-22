package com.postwerk.dto;

import java.util.List;

/**
 * Full detail representation of a marketplace listing.
 *
 * <p>Adds reviews to the base listing, plus — depending on visibility — either a PUBLIC node-flow
 * preview ({@code nodeFlow}) or the PRIVATE publishable-constant metadata ({@code publishableConstants}).</p>
 */
public record MarketplaceListingDetailResponse(
        MarketplaceListingResponse listing,
        List<NodeChipDto> nodeFlow,
        List<PublishableConstantDto> publishableConstants,
        List<ReviewResponse> reviews
) {}
