package com.postwerk.model;

import com.postwerk.model.enums.AudienceScope;
import com.postwerk.model.enums.FeatureFlagKind;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A feature flag — a named toggle gating a feature. {@code rollout} (0–100) is a stored intent; the
 * standard impl when wired is deterministic per-user bucketing (stable hash of userId + key).
 * Display status is derived (ON / ROLLING / OFF / KILLED / ARCHIVED). {@code onSinceAt} marks when it
 * last became fully-on (100% / EVERYONE) for staleness detection.
 *
 * @since 1.0
 */
@Entity
@Table(name = "feature_flags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flag_key", nullable = false, unique = true, length = 120)
    private String flagKey;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FeatureFlagKind kind = FeatureFlagKind.RELEASE;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(nullable = false)
    @Builder.Default
    private int rollout = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AudienceScope audience = AudienceScope.EVERYONE;

    /** CSV of plan names when audience = PLAN. */
    @Column(name = "audience_plans", length = 200)
    private String audiencePlans;

    @Column(name = "audience_org_id")
    private UUID audienceOrgId;

    @Column(name = "audience_org_name", length = 200)
    private String audienceOrgName;

    @Column(nullable = false)
    @Builder.Default
    private boolean killed = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    /** When the flag last became fully-on (100% / EVERYONE); null otherwise. Drives staleness. */
    @Column(name = "on_since_at")
    private Instant onSinceAt;

    @Column(name = "created_by_name", length = 200)
    private String createdByName;

    @Column(name = "updated_by_name", length = 200)
    private String updatedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
