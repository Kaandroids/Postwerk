package com.postwerk.repository;

import com.postwerk.model.MarketplaceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MarketplaceReview} entities.
 *
 * @since 1.0
 */
public interface MarketplaceReviewRepository extends JpaRepository<MarketplaceReview, UUID> {

    List<MarketplaceReview> findByListingIdOrderByCreatedAtDesc(UUID listingId);

    /** Public listing detail — excludes staff-hidden reviews. */
    List<MarketplaceReview> findByListingIdAndHiddenFalseOrderByCreatedAtDesc(UUID listingId);

    Optional<MarketplaceReview> findByListingIdAndUserId(UUID listingId, UUID userId);

    /** Returns {@code [count, avg]} of NON-hidden ratings for a listing (hidden reviews don't count). */
    @Query("SELECT COUNT(r), COALESCE(AVG(r.rating), 0) FROM MarketplaceReview r WHERE r.listingId = :listingId AND r.hidden = false")
    Object[] aggregateRating(@Param("listingId") UUID listingId);

    // Admin moderation — every review across all listings, newest first.
    List<MarketplaceReview> findAllByOrderByCreatedAtDesc();
}
