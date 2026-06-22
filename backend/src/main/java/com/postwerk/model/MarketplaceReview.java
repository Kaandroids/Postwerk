package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a user review of a marketplace listing (one per user per listing).
 */
@Entity
@Table(name = "marketplace_reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String text;

    /** Staff moderation state (admin Marketplace Moderation) — hidden reviews are excluded from the
     *  public listing detail and from the rating average. */
    @Column(nullable = false)
    @Builder.Default
    private boolean hidden = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
