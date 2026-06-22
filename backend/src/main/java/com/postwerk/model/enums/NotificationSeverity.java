package com.postwerk.model.enums;

/**
 * Importance of a notification, driving UI accent and channel-forcing rules. In-app delivery cannot
 * be disabled for {@link #CRITICAL} / {@link #ACTION_REQUIRED} (see {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}).
 *
 * @since 1.0
 */
public enum NotificationSeverity {
    /** Informational; safe to mute. */
    INFO,
    /** A positive outcome worth surfacing. */
    SUCCESS,
    /** Something needs attention but is not breaking. */
    WARNING,
    /** Something is broken and needs action (e.g. mailbox disconnected, quota exhausted). */
    CRITICAL,
    /** The user must act for the workflow to proceed (e.g. an approval is pending). */
    ACTION_REQUIRED
}
