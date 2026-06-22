package com.postwerk.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A notification as returned to the client. {@code titleKey}/{@code bodyKey} + {@code params} are
 * rendered into the user's language on the frontend (DE/EN). See {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
public record NotificationResponse(UUID id,
                                   String category,
                                   String type,
                                   String severity,
                                   String titleKey,
                                   String bodyKey,
                                   Map<String, Object> params,
                                   String linkUrl,
                                   Map<String, Object> payload,
                                   UUID organizationId,
                                   boolean read,
                                   Instant createdAt) {
}
