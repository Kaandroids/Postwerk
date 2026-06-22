package com.postwerk.repository;

import com.postwerk.model.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for feature flags. The admin console filters/sorts/paginates in-memory over
 * {@link #findAllByOrderByUpdatedAtDesc()} (flag volume is low) and computes KPIs from it.
 *
 * @since 1.0
 */
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    List<FeatureFlag> findAllByOrderByUpdatedAtDesc();

    boolean existsByFlagKey(String flagKey);
}
