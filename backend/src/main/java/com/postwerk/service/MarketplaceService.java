package com.postwerk.service;

import com.postwerk.dto.*;

import java.util.List;
import java.util.UUID;

/**
 * Service for the automation marketplace: publishing listings, discovery, installing
 * (buyer-owned hidden copies), configuring installed copies, the buyer library, and reviews.
 *
 * <p>An install deep-copies the source automation and clones its referenced resources into the
 * buyer's account (see {@link MarketplaceResourceCloner} + {@link AutomationService#installCopy}).
 * Payment is metadata-only — acquiring creates an entitlement without charging.</p>
 *
 * @since 1.0
 */
public interface MarketplaceService {

    MarketplaceListingDetailResponse publish(UUID organizationId, UUID userId, PublishListingRequest request);

    List<MarketplaceListingResponse> discover(UUID userId, String category, String sort, String q);

    MarketplaceListingDetailResponse getDetail(UUID userId, UUID listingId);

    /** Installs a listing into the caller's active organization (#4): the install spend lands there. */
    MarketplaceAcquisitionResponse install(UUID organizationId, UUID userId, UUID listingId);

    /** The active organization's installed automations. */
    MarketplaceLibraryResponse getLibrary(UUID organizationId);

    /** Saves buyer-overridable constant values onto an installed (hidden) automation. */
    void saveAcquisitionConstants(UUID organizationId, UUID acquisitionId, List<AutomationConstantDto> constants);

    /** Binds the org's email accounts to the installed automation's trigger. */
    void bindAccounts(UUID organizationId, UUID acquisitionId, List<UUID> accountIds);

    /** Activates the installed automation (sets status ACTIVE). */
    MarketplaceAcquisitionResponse activate(UUID organizationId, UUID acquisitionId);

    List<ReviewResponse> getReviews(UUID userId, UUID listingId);

    ReviewResponse addReview(UUID userId, UUID listingId, ReviewRequest request);

    /** Unpublishes a listing owned by the author. */
    void unpublish(UUID userId, UUID listingId);
}
