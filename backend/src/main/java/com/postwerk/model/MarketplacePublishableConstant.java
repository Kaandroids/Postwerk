package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * JPA entity declaring which of a PRIVATE listing's automation constants are buyer-overridable,
 * along with an author-supplied description shown in the Configure surface.
 */
@Entity
@Table(name = "marketplace_publishable_constants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplacePublishableConstant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
