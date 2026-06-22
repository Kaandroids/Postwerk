package com.postwerk.repository;

import com.postwerk.model.AnnouncementEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link AnnouncementEvent} change-history entries.
 *
 * @since 1.0
 */
public interface AnnouncementEventRepository extends JpaRepository<AnnouncementEvent, UUID> {

    List<AnnouncementEvent> findByAnnouncementIdOrderByCreatedAtAsc(UUID announcementId);
}
