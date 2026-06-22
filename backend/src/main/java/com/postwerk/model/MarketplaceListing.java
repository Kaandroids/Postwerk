package com.postwerk.model;

import com.postwerk.model.enums.AutomationKind;
import com.postwerk.model.enums.ListingStatus;
import com.postwerk.model.enums.ListingVisibility;
import com.postwerk.model.enums.PricingModel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a published marketplace listing for an automation.
 *
 * <p>A listing references the author's source {@link Automation}. On install the marketplace
 * deep-copies the automation (and clones its referenced resources) into the buyer's account.
 * Snapshot facts ({@code nodeCount}, {@code constantCount}, io labels) are captured at publish
 * time so the discover/detail surfaces never need to read the source automation.
 * Soft-deleted via {@code deletedAt}.</p>
 */
@Entity
@Table(name = "marketplace_listings")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The listing's automation pointer. With the snapshot model this points at the frozen snapshot
     * source for the live-read fallback; {@link #sourceAutomationId} records the author's original.
     */
    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    /** The author's original automation this listing was published from (snapshot provenance / re-publish). */
    @Column(name = "source_automation_id")
    private UUID sourceAutomationId;

    /** Per-referenced-KB entry-sharing policy at publish: {@code { "<kbId>": "SCHEMA_ONLY" | "FULL" }}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kb_share_policy", columnDefinition = "JSONB")
    private String kbSharePolicy;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** Owning organization (the publisher's org) — multi-tenant model (#4). */
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(length = 280)
    private String tagline;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 40)
    private String category;

    /** Discriminator mirroring the published automation's kind: {@code AUTOMATION} or {@code INTEGRATION}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AutomationKind kind = AutomationKind.AUTOMATION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ListingVisibility visibility = ListingVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model", nullable = false, length = 16)
    @Builder.Default
    private PricingModel pricingModel = PricingModel.FREE;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String version = "1.0.0";

    @Column(length = 40)
    private String icon;

    @Column(length = 40)
    private String color;

    @Column(name = "io_in_icon", length = 40)
    private String ioInIcon;

    @Column(name = "io_in_label", length = 120)
    private String ioInLabel;

    @Column(name = "io_out_icon", length = 40)
    private String ioOutIcon;

    @Column(name = "io_out_label", length = 120)
    private String ioOutLabel;

    @Column(name = "node_count", nullable = false)
    @Builder.Default
    private int nodeCount = 0;

    @Column(name = "constant_count", nullable = false)
    @Builder.Default
    private int constantCount = 0;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private int ratingCount = 0;

    @Column(name = "install_count", nullable = false)
    @Builder.Default
    private int installCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean featured = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    /** Staff moderation state (admin Marketplace Moderation) — hides the listing from discovery/detail
     *  while preserving buyers' installed copies. Separate from the author-owned {@link #status}. */
    @Column(name = "taken_down", nullable = false)
    @Builder.Default
    private boolean takenDown = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ListingStatus status = ListingStatus.PUBLISHED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
