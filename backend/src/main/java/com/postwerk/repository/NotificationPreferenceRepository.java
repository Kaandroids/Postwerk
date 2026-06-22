package com.postwerk.repository;

import com.postwerk.model.NotificationPreference;
import com.postwerk.model.enums.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link NotificationPreference} rows (per-user, per-category toggles).
 *
 * @since 1.0
 */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findByUserId(UUID userId);

    Optional<NotificationPreference> findByUserIdAndCategory(UUID userId, NotificationCategory category);
}
