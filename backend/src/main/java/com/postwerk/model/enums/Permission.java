package com.postwerk.model.enums;

/**
 * Fine-grained capability within an organization (multi-tenant model #4).
 *
 * <p>Permissions are bundled into {@link OrgRole}s; authorization call sites check a discrete
 * {@code Permission} (never a role directly) so custom roles can be introduced later without
 * touching call sites. Mailbox-scoped permissions ({@link #MAILBOX_READ} / {@link #MAILBOX_SEND})
 * additionally require a per-mailbox grant (see {@code MailboxGrant}); Owner/Admin bypass the grant.</p>
 *
 * @since 1.0
 */
public enum Permission {
    // Mailbox (mailbox-scoped where noted)
    MAILBOX_CONNECT,   // connect/disconnect email accounts (IMAP/SMTP config)
    MAILBOX_READ,      // read emails in a granted mailbox
    MAILBOX_SEND,      // send/reply/forward as a granted mailbox (sensitive)
    MAILBOX_FOLDERS,   // create/delete folders, move emails

    // Automation
    AUTOMATION_VIEW,
    AUTOMATION_EDIT,
    AUTOMATION_ACTIVATE,  // turn on live side effects (sensitive)
    AUTOMATION_TEST,      // dry-run tests / simulations

    // Approvals (#3 supervised queue)
    APPROVAL_VIEW,
    APPROVAL_DECIDE,      // approve/reject pending actions = performs real side effects (sensitive)

    // Shared resources
    RESOURCE_VIEW,        // categories / templates / parameter sets / constants
    RESOURCE_EDIT,
    SECRET_MANAGE,        // view/set secret constants & webhook secrets (sensitive)

    // Marketplace
    MARKETPLACE_PUBLISH,
    MARKETPLACE_INSTALL,

    // Organization management
    MEMBER_INVITE,
    MEMBER_MANAGE,        // change roles / remove members
    ORG_SETTINGS,         // org name, GDPR/retention settings
    BILLING_MANAGE,       // manage plan / seats / payment (sensitive)
    AUDIT_VIEW            // activity feed / audit log
}
