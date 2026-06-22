package com.postwerk.model.enums;

/**
 * Fine-grained platform-staff capability (admin panel). Mirrors the org-level {@link Permission}
 * pattern: capabilities are bundled into {@link StaffRole}s and emitted as Spring authorities so
 * admin endpoints authorize on a discrete capability ({@code @PreAuthorize("hasAuthority('…')")})
 * rather than a role name — new staff roles can be introduced later without touching call sites.
 *
 * <p>Distinct from {@link Permission}, which scopes capabilities WITHIN a single customer
 * organization. A {@code StaffPermission} scopes capabilities across the WHOLE platform and is
 * only ever granted to employees (a user is "staff" iff they have a non-null {@link StaffRole}).</p>
 *
 * @since 1.0
 */
public enum StaffPermission {
    // Overview
    PLATFORM_DASHBOARD_VIEW,

    // Customers — users
    USER_VIEW,
    USER_MANAGE,               // change platform/staff role, disable/enable, delete (sensitive)
    USER_CREDENTIAL_RESET,     // force password reset, resend verification
    // NOTE: customer impersonation ("log in as user") is intentionally NOT a capability of this
    // platform — deemed too high-risk (a compromised staff account would expose every customer).

    // Customers — organizations
    ORG_VIEW,
    ORG_MANAGE,                // suspend, transfer ownership, adjust seats, delete (sensitive)

    // Billing & plans
    PLAN_VIEW,
    PLAN_MANAGE,               // plan CRUD + assign plan to a user
    BILLING_VIEW,
    BILLING_MANAGE,            // invoices, refunds, credits (sensitive)
    QUOTA_OVERRIDE,            // grant AI credit / override quota caps per user or org

    // AI & automation oversight
    AI_USAGE_VIEW,
    AUTOMATION_OVERSIGHT_VIEW,

    // Infrastructure
    INFRA_VIEW,                // system / email (IMAP/SMTP) / background-job health
    INFRA_MANAGE,              // trigger jobs, retry stuck syncs (sensitive)

    // Marketplace
    MARKETPLACE_MODERATE,      // approve / reject / take down listings & reviews

    // Compliance (GDPR / DSGVO)
    COMPLIANCE_VIEW,           // data-request queue, retention status
    COMPLIANCE_MANAGE,         // execute DSAR export / erasure, retention overrides (sensitive)
    AUDIT_LOG_VIEW,

    // System
    FEATURE_FLAG_MANAGE,
    ANNOUNCEMENT_MANAGE,       // broadcast announcements / maintenance banners
    STAFF_MANAGE,              // create staff, assign staff roles (super-admin only)
    PROMPT_MANAGE              // edit AI prompt templates platform-wide (super-admin only)
}
