package com.postwerk.service;

import com.postwerk.dto.admin.AdminListingDetailResponse;
import com.postwerk.dto.admin.AdminListingResponse;
import com.postwerk.dto.admin.AdminReviewResponse;
import com.postwerk.dto.admin.MarketplaceKpisResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Platform-staff Marketplace Moderation: cross-tenant listing + review moderation (take down / restore
 * / feature, hide / unhide / delete), gated by {@code MARKETPLACE_MODERATE} (enforced at the controller).
 *
 * @since 1.0
 */
public interface AdminMarketplaceService {

    Page<AdminListingResponse> listListings(String search, String status, String kind, String pricing,
                                            String sort, Pageable pageable);

    Page<AdminReviewResponse> listReviews(String search, Integer rating, String status, String sort, Pageable pageable);

    MarketplaceKpisResponse kpis();

    AdminListingDetailResponse getListing(UUID listingId);

    AdminListingResponse takeDown(UUID listingId, String reason, UUID actorUserId, String ip);

    AdminListingResponse restore(UUID listingId, UUID actorUserId, String ip);

    AdminListingResponse setFeatured(UUID listingId, boolean featured, UUID actorUserId, String ip);

    AdminReviewResponse setReviewHidden(UUID reviewId, boolean hidden, UUID actorUserId, String ip);

    void deleteReview(UUID reviewId, String reason, UUID actorUserId, String ip);
}
