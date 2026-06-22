package com.postwerk.repository;

import com.postwerk.model.MarketplaceListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MarketplaceListing} entities.
 * Provides the discover query (filter by optional category + text, multiple sort orders)
 * and author/automation lookups.
 *
 * @since 1.0
 */
public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, UUID> {

    Optional<MarketplaceListing> findByIdAndDeletedAtIsNull(UUID id);

    List<MarketplaceListing> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);

    // Org-scoped (#4): listings published by the active organization (for the library's published list).
    List<MarketplaceListing> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<MarketplaceListing> findByAutomationIdAndDeletedAtIsNull(UUID automationId);

    /**
     * Discover query over PUBLISHED, non-deleted listings. {@code category} and {@code q} are optional
     * (null = no filter). {@code sort} switches the order: {@code popular} (featured then installs),
     * {@code new} (created_at desc), {@code installs} (install_count desc), {@code rating} (rating_avg desc).
     */
    @Query("""
            SELECT l FROM MarketplaceListing l
            WHERE l.status = com.postwerk.model.enums.ListingStatus.PUBLISHED
              AND l.deletedAt IS NULL
              AND l.takenDown = false
              AND (:category IS NULL OR l.category = :category)
              AND (:q IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                              OR LOWER(l.tagline) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            ORDER BY
              CASE WHEN :sort = 'popular' THEN (CASE WHEN l.featured = true THEN 1 ELSE 0 END) ELSE 0 END DESC,
              CASE WHEN :sort = 'new' THEN l.createdAt END DESC,
              CASE WHEN :sort = 'installs' THEN l.installCount END DESC,
              CASE WHEN :sort = 'rating' THEN l.ratingAvg END DESC,
              CASE WHEN :sort = 'popular' THEN l.installCount END DESC,
              l.createdAt DESC
            """)
    List<MarketplaceListing> discover(@Param("category") String category,
                                       @Param("q") String q,
                                       @Param("sort") String sort);

    // Admin moderation — every listing across all tenants (the @SQLRestriction already excludes deleted).
    List<MarketplaceListing> findAllByOrderByCreatedAtDesc();
}
