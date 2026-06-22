package com.postwerk.model.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Organization-level role (multi-tenant model #4). Each role maps to a bundle of {@link Permission}s
 * via {@link #permissions()}. The shape is <b>two entry roles + a builder ladder</b>: VIEWER and
 * MEMBER are parallel low roles (observer vs. operator — neither contains the other), and above them
 * EDITOR ⊂ ADMIN ⊂ OWNER each strictly add to the one below. Owner/Admin get implicit access to every
 * org mailbox; Editor/Member/Viewer need explicit per-mailbox grants.
 *
 * <p>Personas (separation of concerns): the <b>Viewer</b> only observes (read-only, incl. automations
 * &amp; audit — for auditors/managers/clients); the <b>Member</b> is the front-line agent who works the
 * inbox (read/send granted boxes) and the approval queue, and nothing else (no automation-building
 * surface); the <b>Editor</b> builds &amp; tests automations/resources but cannot take them live; the
 * <b>Admin/Owner</b> approves &amp; activates and manages the org.</p>
 *
 * @since 1.0
 */
public enum OrgRole {
    /** Full control incl. billing, org deletion and ownership transfer. One per org (transferable). */
    OWNER,
    /** Everything except billing/org-deletion/ownership-transfer. Activates automations. Implicit all-mailbox access. */
    ADMIN,
    /** Builds, edits and tests automations &amp; resources; installs from the marketplace. Cannot activate (go live). */
    EDITOR,
    /** Front-line agent: works granted inboxes (read/send) and the approval queue. No automation-building surface. */
    MEMBER,
    /** Read-only observer (automations, resources, approvals, audit; reads granted mailboxes). */
    VIEWER;

    /** The permission bundle granted by this role. */
    public Set<Permission> permissions() {
        return switch (this) {
            case OWNER -> EnumSet.allOf(Permission.class);
            case ADMIN -> {
                EnumSet<Permission> s = EnumSet.allOf(Permission.class);
                s.remove(Permission.BILLING_MANAGE);
                yield s;
            }
            // EDITOR = operator base + view/build/test/install. No AUTOMATION_ACTIVATE (only Admin/Owner go live).
            case EDITOR -> {
                EnumSet<Permission> s = EnumSet.copyOf(operatorBase());
                s.add(Permission.AUTOMATION_VIEW);
                s.add(Permission.AUTOMATION_EDIT);
                s.add(Permission.AUTOMATION_TEST);
                s.add(Permission.RESOURCE_VIEW);
                s.add(Permission.RESOURCE_EDIT);
                s.add(Permission.MARKETPLACE_INSTALL);
                s.add(Permission.AUDIT_VIEW);
                yield s;
            }
            // MEMBER (agent) = just the operator base: inbox + approval queue, no building/observing surface.
            case MEMBER -> operatorBase();
            case VIEWER -> EnumSet.of(
                    Permission.MAILBOX_READ,
                    Permission.AUTOMATION_VIEW, Permission.RESOURCE_VIEW,
                    Permission.APPROVAL_VIEW, Permission.AUDIT_VIEW);
        };
    }

    /** Operator base shared by MEMBER (agent) and EDITOR: work granted inboxes + decide the approval queue. */
    private static EnumSet<Permission> operatorBase() {
        return EnumSet.of(
                Permission.MAILBOX_READ, Permission.MAILBOX_SEND, Permission.MAILBOX_FOLDERS,
                Permission.APPROVAL_VIEW, Permission.APPROVAL_DECIDE);
    }

    /** Whether this role grants the given permission (ignores per-mailbox grant gating). */
    public boolean has(Permission permission) {
        return permissions().contains(permission);
    }
}
