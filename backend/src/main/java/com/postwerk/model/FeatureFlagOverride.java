package com.postwerk.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A per-segment force-on / force-off override on a {@link FeatureFlag} (e.g. "ENTERPRISE → on",
 * "{org} → off"). {@code scope} is a plan name, org name, or "Staff"; {@code value} is "on"/"off".
 *
 * @since 1.0
 */
@Entity
@Table(name = "feature_flag_overrides")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flag_id", nullable = false)
    private UUID flagId;

    @Column(nullable = false, length = 200)
    private String scope;

    @Column(nullable = false, length = 8)
    private String value;
}
