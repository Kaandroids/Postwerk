package com.postwerk.dto.admin;

/**
 * KPI strip totals for both segments of the admin Marketplace Moderation screen.
 *
 * <p>{@code pendingListings}/{@code pendingReviews} are always 0 today — there is no user-report /
 * flag store yet (a moderation queue would need one); the FE shows them as the "flagged/pending" KPI.</p>
 */
public record MarketplaceKpisResponse(
        // Listings segment
        long totalListings,
        long publishedListings,
        long pausedListings,
        long takenDownListings,
        long totalInstalls,
        double avgRating,
        long pendingListings,
        // Reviews segment
        long totalReviews,
        long visibleReviews,
        long hiddenReviews,
        double reviewAvgRating,
        long lowRatings,
        long pendingReviews
) {}
