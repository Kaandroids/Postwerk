package com.postwerk.dto;

import java.util.List;

/**
 * The buyer's marketplace library, split into the three Library tabs.
 *
 * @param installed all active acquisitions
 * @param purchased active acquisitions whose pricing model is not FREE (real entitlements)
 * @param published listings authored by the user
 */
public record MarketplaceLibraryResponse(
        List<MarketplaceAcquisitionResponse> installed,
        List<MarketplaceAcquisitionResponse> purchased,
        List<MarketplaceListingResponse> published
) {}
