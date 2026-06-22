package com.postwerk.repository;

import com.postwerk.model.FeatureFlagOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link FeatureFlagOverride} per-segment force-on/off rows.
 *
 * @since 1.0
 */
public interface FeatureFlagOverrideRepository extends JpaRepository<FeatureFlagOverride, UUID> {

    List<FeatureFlagOverride> findByFlagId(UUID flagId);

    List<FeatureFlagOverride> findByFlagIdIn(Collection<UUID> flagIds);

    void deleteByFlagId(UUID flagId);
}
