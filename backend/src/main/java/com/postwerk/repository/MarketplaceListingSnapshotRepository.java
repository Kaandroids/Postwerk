package com.postwerk.repository;

import com.postwerk.model.MarketplaceListingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MarketplaceListingSnapshot} — one frozen manifest per listing.
 *
 * @since 1.0
 */
public interface MarketplaceListingSnapshotRepository extends JpaRepository<MarketplaceListingSnapshot, UUID> {

    Optional<MarketplaceListingSnapshot> findByListingId(UUID listingId);

    void deleteByListingId(UUID listingId);
}
