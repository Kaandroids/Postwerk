package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable publish-time snapshot of a {@link MarketplaceListing}'s automation: a serialized manifest
 * (automation fields + nodes + edges + referenced resources) frozen when the author publishes. Install
 * materializes the buyer's copy from this manifest rather than the author's live (mutable/deletable)
 * data. One row per listing (re-publish replaces it + bumps {@code version}). Listings without a
 * snapshot fall back to the legacy live-read path (backward compatible).
 *
 * @since 1.0
 */
@Entity
@Table(name = "marketplace_listing_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceListingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String version = "1.0.0";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB", nullable = false)
    private String manifest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
