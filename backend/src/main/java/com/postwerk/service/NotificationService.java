package com.postwerk.service;

import com.postwerk.dto.NotificationListResponse;
import com.postwerk.dto.NotificationPreferenceDto;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Creates, queries, and manages user-facing notifications and their delivery preferences. Producers
 * never call this directly — they publish a domain event; an {@code @EventListener} resolves recipients
 * and calls {@link #create}. See {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
public interface NotificationService {

    /**
     * Persists one notification per recipient, honoring per-recipient dedup and the in-app preference
     * gate (in-app is forced on for CRITICAL/ACTION_REQUIRED). No-op when {@code recipientUserIds} is empty.
     */
    void create(Collection<UUID> recipientUserIds, NewNotification spec);

    /** One page of the user's notifications (optionally unread-only) plus the live unread count. */
    NotificationListResponse list(UUID userId, boolean unreadOnly, Pageable pageable);

    long unreadCount(UUID userId);

    void markRead(UUID userId, UUID id);

    void markAllRead(UUID userId);

    /** The full preference matrix (one row per category, defaults filled where no row exists). */
    List<NotificationPreferenceDto> getPreferences(UUID userId);

    /** Upserts the given preference rows and returns the refreshed full matrix. */
    List<NotificationPreferenceDto> updatePreferences(UUID userId, List<NotificationPreferenceDto> preferences);
}
