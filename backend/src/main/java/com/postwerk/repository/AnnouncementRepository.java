package com.postwerk.repository;

import com.postwerk.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for platform announcements. The admin console filters/sorts/paginates in-memory over
 * {@link #findAllByOrderByUpdatedAtDesc()} (announcement volume is low) and computes KPIs from it.
 *
 * @since 1.0
 */
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    List<Announcement> findAllByOrderByUpdatedAtDesc();
}
