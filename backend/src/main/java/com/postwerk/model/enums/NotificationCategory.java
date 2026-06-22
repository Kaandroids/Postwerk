package com.postwerk.model.enums;

/**
 * Top-level grouping of a {@link NotificationType}, used for filtering and as the granularity of a
 * user's {@code NotificationPreference} (one channel toggle per category). See
 * {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
public enum NotificationCategory {
    /** Automation run outcomes (e.g. a flow failed). */
    AUTOMATION,
    /** Supervised-mode approvals awaiting a human decision. */
    APPROVAL,
    /** AI cost / resource quota warnings and caps. */
    QUOTA,
    /** Email-account sync / connection / auth health. */
    MAILBOX,
    /** Organization membership lifecycle (invites, role changes, suspension). */
    TEAM,
    /** Marketplace activity (installs, reviews) for a listing's author. */
    MARKETPLACE,
    /** Platform messages, admin announcements, and in-flow NOTIFY-node alerts. */
    SYSTEM
}
