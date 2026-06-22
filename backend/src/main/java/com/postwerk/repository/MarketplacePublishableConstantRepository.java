package com.postwerk.repository;

import com.postwerk.model.MarketplacePublishableConstant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MarketplacePublishableConstant} declarations.
 *
 * @since 1.0
 */
public interface MarketplacePublishableConstantRepository extends JpaRepository<MarketplacePublishableConstant, UUID> {

    List<MarketplacePublishableConstant> findByListingIdOrderBySortOrderAsc(UUID listingId);

    void deleteByListingId(UUID listingId);
}
