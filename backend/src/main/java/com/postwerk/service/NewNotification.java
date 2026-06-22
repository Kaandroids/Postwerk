package com.postwerk.service;

import com.postwerk.model.enums.NotificationCategory;
import com.postwerk.model.enums.NotificationSeverity;
import com.postwerk.model.enums.NotificationType;

import java.util.Map;
import java.util.UUID;

/**
 * The content of a notification to create, independent of who receives it (recipients are resolved
 * separately and passed alongside). {@code categoryOverride}/{@code severityOverride} are null for
 * system events (the {@link NotificationType} defaults apply) and set by the NOTIFY automation node.
 *
 * @since 1.0
 */
public record NewNotification(NotificationType type,
                              UUID organizationId,
                              NotificationSeverity severityOverride,
                              NotificationCategory categoryOverride,
                              Map<String, Object> params,
                              String linkUrl,
                              Map<String, Object> payload,
                              String dedupKey) {
}
