package com.postwerk.repository;

import com.postwerk.model.MarketplaceAcquisition;
import com.postwerk.model.enums.AcquisitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MarketplaceAcquisition} entitlements.
 *
 * @since 1.0
 */
public interface MarketplaceAcquisitionRepository extends JpaRepository<MarketplaceAcquisition, UUID> {

    List<MarketplaceAcquisition> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, AcquisitionStatus status);

    Optional<MarketplaceAcquisition> findByIdAndUserId(UUID id, UUID userId);

    // Org-scoped (#4): the org's library + acquisition access for any member.
    List<MarketplaceAcquisition> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, AcquisitionStatus status);

    Optional<MarketplaceAcquisition> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** Org-scoped duplicate-install guard (#4): the same listing may live in different orgs of one user. */
    Optional<MarketplaceAcquisition> findByOrganizationIdAndListingIdAndStatus(UUID organizationId, UUID listingId, AcquisitionStatus status);
}
