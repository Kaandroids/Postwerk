package com.postwerk.repository;

import com.postwerk.model.FeatureFlagEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link FeatureFlagEvent} change-history entries.
 *
 * @since 1.0
 */
public interface FeatureFlagEventRepository extends JpaRepository<FeatureFlagEvent, UUID> {

    List<FeatureFlagEvent> findByFlagIdOrderByCreatedAtAsc(UUID flagId);
}
