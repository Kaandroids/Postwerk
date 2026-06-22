package com.postwerk.dto;

import java.util.List;

/**
 * One page of a user's notifications plus the live unread count (so the bell badge and the dropdown
 * fill from a single request) and the total for pagination.
 *
 * @since 1.0
 */
public record NotificationListResponse(List<NotificationResponse> items,
                                       long unreadCount,
                                       long total) {
}
