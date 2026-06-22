package com.postwerk.model.enums;

/**
 * Concrete notification kind. Each constant carries its {@link NotificationCategory}, a default
 * {@link NotificationSeverity}, and the i18n keys for its title/body — so producers stay terse and
 * the rendered text lives in the frontend (DE/EN). A producer may override category/severity per
 * notification (the {@link #NOTIFY_NODE} in-flow alert does, and passes literal title/body via params).
 *
 * @since 1.0
 */
public enum NotificationType {

    // ── APPROVAL ──
    APPROVAL_PENDING(NotificationCategory.APPROVAL, NotificationSeverity.ACTION_REQUIRED,
            "notif_type_approval_pending_title", "notif_type_approval_pending_body"),
    APPROVAL_DECIDED(NotificationCategory.APPROVAL, NotificationSeverity.INFO,
            "notif_type_approval_decided_title", "notif_type_approval_decided_body"),

    // ── AUTOMATION ──
    AUTOMATION_FAILED(NotificationCategory.AUTOMATION, NotificationSeverity.WARNING,
            "notif_type_automation_failed_title", "notif_type_automation_failed_body"),

    // ── QUOTA ──
    QUOTA_WARNING(NotificationCategory.QUOTA, NotificationSeverity.WARNING,
            "notif_type_quota_warning_title", "notif_type_quota_warning_body"),
    QUOTA_EXCEEDED(NotificationCategory.QUOTA, NotificationSeverity.CRITICAL,
            "notif_type_quota_exceeded_title", "notif_type_quota_exceeded_body"),

    // ── MAILBOX ──
    MAILBOX_AUTH_ERROR(NotificationCategory.MAILBOX, NotificationSeverity.CRITICAL,
            "notif_type_mailbox_auth_error_title", "notif_type_mailbox_auth_error_body"),
    MAILBOX_CONN_ERROR(NotificationCategory.MAILBOX, NotificationSeverity.WARNING,
            "notif_type_mailbox_conn_error_title", "notif_type_mailbox_conn_error_body"),

    // ── TEAM ──
    TEAM_INVITED(NotificationCategory.TEAM, NotificationSeverity.ACTION_REQUIRED,
            "notif_type_team_invited_title", "notif_type_team_invited_body"),
    TEAM_ROLE_CHANGED(NotificationCategory.TEAM, NotificationSeverity.INFO,
            "notif_type_team_role_changed_title", "notif_type_team_role_changed_body"),
    TEAM_SUSPENDED(NotificationCategory.TEAM, NotificationSeverity.WARNING,
            "notif_type_team_suspended_title", "notif_type_team_suspended_body"),

    // ── MARKETPLACE ──
    MARKETPLACE_INSTALLED(NotificationCategory.MARKETPLACE, NotificationSeverity.INFO,
            "notif_type_marketplace_installed_title", "notif_type_marketplace_installed_body"),
    MARKETPLACE_REVIEWED(NotificationCategory.MARKETPLACE, NotificationSeverity.INFO,
            "notif_type_marketplace_reviewed_title", "notif_type_marketplace_reviewed_body"),

    // ── SYSTEM ──
    /** In-flow alert from a NOTIFY automation node. Title/body are passed literally via params
     *  (the i18n keys are passthrough renderers); category/severity may be overridden per node config. */
    NOTIFY_NODE(NotificationCategory.SYSTEM, NotificationSeverity.INFO,
            "notif_type_notify_node_title", "notif_type_notify_node_body"),
    ANNOUNCEMENT(NotificationCategory.SYSTEM, NotificationSeverity.INFO,
            "notif_type_announcement_title", "notif_type_announcement_body");

    private final NotificationCategory category;
    private final NotificationSeverity defaultSeverity;
    private final String titleKey;
    private final String bodyKey;

    NotificationType(NotificationCategory category, NotificationSeverity defaultSeverity,
                     String titleKey, String bodyKey) {
        this.category = category;
        this.defaultSeverity = defaultSeverity;
        this.titleKey = titleKey;
        this.bodyKey = bodyKey;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public NotificationSeverity getDefaultSeverity() {
        return defaultSeverity;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getBodyKey() {
        return bodyKey;
    }
}
