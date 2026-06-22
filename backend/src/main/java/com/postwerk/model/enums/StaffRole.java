package com.postwerk.model.enums;

import java.util.EnumSet;
import java.util.Set;

import static com.postwerk.model.enums.StaffPermission.*;

/**
 * Platform-staff role tier for the admin panel, layered on top of the coarse {@link Role}
 * (USER/ADMIN). A user is "platform staff" iff they have a non-null staff role. Each role maps to
 * a bundle of {@link StaffPermission}s (mirrors the org-level {@link OrgRole} → {@link Permission}
 * pattern); the bundle is emitted as Spring authorities for {@code @PreAuthorize} checks.
 *
 * @since 1.0
 */
public enum StaffRole {
    /** Unrestricted platform access, incl. managing other staff and editing AI prompt templates. */
    SUPER_ADMIN,
    /** Everything except staff management and prompt editing. */
    ADMIN,
    /** Customer support: read access + credential resets and quota grants. */
    SUPPORT,
    /** Finance: plans, billing, refunds and quota overrides; read-only on customer content. */
    BILLING,
    /** Marketplace moderation only. */
    MODERATOR,
    /** Read-only / compliance auditor: every view + audit + compliance, no writes. */
    AUDITOR;

    /** The capability bundle granted by this role. */
    public Set<StaffPermission> permissions() {
        return switch (this) {
            case SUPER_ADMIN -> EnumSet.allOf(StaffPermission.class);
            case ADMIN -> {
                EnumSet<StaffPermission> s = EnumSet.allOf(StaffPermission.class);
                s.remove(STAFF_MANAGE);
                s.remove(PROMPT_MANAGE);
                yield s;
            }
            case SUPPORT -> EnumSet.of(
                    PLATFORM_DASHBOARD_VIEW,
                    USER_VIEW, USER_CREDENTIAL_RESET,
                    ORG_VIEW, PLAN_VIEW, BILLING_VIEW, QUOTA_OVERRIDE,
                    AI_USAGE_VIEW, AUTOMATION_OVERSIGHT_VIEW,
                    INFRA_VIEW, COMPLIANCE_VIEW, AUDIT_LOG_VIEW);
            case BILLING -> EnumSet.of(
                    PLATFORM_DASHBOARD_VIEW,
                    USER_VIEW, ORG_VIEW,
                    PLAN_VIEW, PLAN_MANAGE, BILLING_VIEW, BILLING_MANAGE, QUOTA_OVERRIDE,
                    AI_USAGE_VIEW, AUDIT_LOG_VIEW);
            case MODERATOR -> EnumSet.of(
                    PLATFORM_DASHBOARD_VIEW,
                    USER_VIEW, ORG_VIEW,
                    MARKETPLACE_MODERATE, AUDIT_LOG_VIEW);
            case AUDITOR -> EnumSet.of(
                    PLATFORM_DASHBOARD_VIEW,
                    USER_VIEW, ORG_VIEW, PLAN_VIEW, BILLING_VIEW,
                    AI_USAGE_VIEW, AUTOMATION_OVERSIGHT_VIEW, INFRA_VIEW,
                    COMPLIANCE_VIEW, AUDIT_LOG_VIEW);
        };
    }

    /** Whether this role grants the given capability. */
    public boolean has(StaffPermission permission) {
        return permissions().contains(permission);
    }
}
