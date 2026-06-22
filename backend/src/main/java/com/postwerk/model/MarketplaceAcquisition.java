package com.postwerk.model;

import com.postwerk.model.enums.AcquisitionStatus;
import com.postwerk.model.enums.PricingModel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a buyer's entitlement to a marketplace listing.
 *
 * <p>Created on install. Links the buyer to the listing and to the buyer-owned installed copy
 * ({@code installedAutomationId}). Payment is metadata-only — {@code pricingModel}/{@code price}
 * are snapshotted but never charged.</p>
 */
@Entity
@Table(name = "marketplace_acquisitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceAcquisition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Owning organization (the buyer's org) — multi-tenant model (#4). */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "installed_automation_id", nullable = false)
    private UUID installedAutomationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model", nullable = false, length = 16)
    @Builder.Default
    private PricingModel pricingModel = PricingModel.FREE;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private AcquisitionStatus status = AcquisitionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
