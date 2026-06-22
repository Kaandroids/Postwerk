package com.postwerk.model.enums;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the permission bundles of the redesigned 5-role model (multi-tenant #4): two parallel entry
 * roles (VIEWER observer / MEMBER agent) + a builder ladder (EDITOR ⊂ ADMIN ⊂ OWNER). These are
 * pure-unit assertions over {@link OrgRole#permissions()} — they are the canonical contract the
 * frontend {@code ROLE_PERMISSIONS} mirror and every authorization call site rely on, so any future
 * edit to a bundle must intentionally update this test.
 */
class OrgRoleTest {

    private static final Set<Permission> OPERATOR_BASE = Set.of(
            Permission.MAILBOX_READ, Permission.MAILBOX_SEND, Permission.MAILBOX_FOLDERS,
            Permission.APPROVAL_VIEW, Permission.APPROVAL_DECIDE);

    // ── Exact bundle per role ────────────────────────────────────────────

    @Test
    void owner_hasEveryPermission() {
        assertThat(OrgRole.OWNER.permissions())
                .containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void admin_hasEverythingExceptBilling() {
        assertThat(OrgRole.ADMIN.permissions())
                .contains(Permission.AUTOMATION_ACTIVATE, Permission.MEMBER_MANAGE,
                        Permission.ORG_SETTINGS, Permission.SECRET_MANAGE, Permission.MARKETPLACE_PUBLISH)
                .doesNotContain(Permission.BILLING_MANAGE)
                .hasSize(Permission.values().length - 1);
    }

    @Test
    void editor_isOperatorBasePlusBuildAndInstall_butNeverActivate() {
        assertThat(OrgRole.EDITOR.permissions()).containsExactlyInAnyOrder(
                // operator base (inbox + approval queue)
                Permission.MAILBOX_READ, Permission.MAILBOX_SEND, Permission.MAILBOX_FOLDERS,
                Permission.APPROVAL_VIEW, Permission.APPROVAL_DECIDE,
                // build & test surface
                Permission.AUTOMATION_VIEW, Permission.AUTOMATION_EDIT, Permission.AUTOMATION_TEST,
                Permission.RESOURCE_VIEW, Permission.RESOURCE_EDIT,
                Permission.MARKETPLACE_INSTALL, Permission.AUDIT_VIEW);
        assertThat(OrgRole.EDITOR.has(Permission.AUTOMATION_ACTIVATE)).isFalse();
        assertThat(OrgRole.EDITOR.has(Permission.MARKETPLACE_PUBLISH)).isFalse();
        assertThat(OrgRole.EDITOR.has(Permission.MEMBER_MANAGE)).isFalse();
    }

    @Test
    void member_isExactlyTheOperatorBase() {
        // The "agent": works granted inboxes + decides the approval queue, nothing else.
        assertThat(OrgRole.MEMBER.permissions()).containsExactlyInAnyOrderElementsOf(OPERATOR_BASE);
        assertThat(OrgRole.MEMBER.has(Permission.AUTOMATION_VIEW)).isFalse();
        assertThat(OrgRole.MEMBER.has(Permission.RESOURCE_VIEW)).isFalse();
    }

    @Test
    void viewer_isReadOnlyObserver() {
        assertThat(OrgRole.VIEWER.permissions()).containsExactlyInAnyOrder(
                Permission.MAILBOX_READ,
                Permission.AUTOMATION_VIEW, Permission.RESOURCE_VIEW,
                Permission.APPROVAL_VIEW, Permission.AUDIT_VIEW);
        // Pure observer: no write/send/decide anywhere.
        assertThat(OrgRole.VIEWER.has(Permission.MAILBOX_SEND)).isFalse();
        assertThat(OrgRole.VIEWER.has(Permission.AUTOMATION_EDIT)).isFalse();
        assertThat(OrgRole.VIEWER.has(Permission.RESOURCE_EDIT)).isFalse();
        assertThat(OrgRole.VIEWER.has(Permission.APPROVAL_DECIDE)).isFalse();
    }

    // ── Structural relationships (the "shape" of the model) ──────────────

    @Test
    void builderLadder_isStrictlyNested() {
        assertThat(OrgRole.OWNER.permissions()).containsAll(OrgRole.ADMIN.permissions());
        assertThat(OrgRole.ADMIN.permissions()).containsAll(OrgRole.EDITOR.permissions());
        assertThat(OrgRole.EDITOR.permissions()).containsAll(OrgRole.MEMBER.permissions());
        assertThat(OrgRole.EDITOR.permissions()).containsAll(OrgRole.VIEWER.permissions());
        // Each step strictly adds something (no two ladder roles are equal).
        assertThat(OrgRole.OWNER.permissions()).hasSizeGreaterThan(OrgRole.ADMIN.permissions().size());
        assertThat(OrgRole.ADMIN.permissions()).hasSizeGreaterThan(OrgRole.EDITOR.permissions().size());
        assertThat(OrgRole.EDITOR.permissions()).hasSizeGreaterThan(OrgRole.MEMBER.permissions().size());
    }

    @Test
    void member_andViewer_areIncomparableEntryRoles() {
        // Neither contains the other: agent can send but not observe automations; viewer the reverse.
        assertThat(OrgRole.MEMBER.permissions()).doesNotContain(Permission.AUTOMATION_VIEW);
        assertThat(OrgRole.VIEWER.permissions()).doesNotContain(Permission.MAILBOX_SEND);
        assertThat(OrgRole.MEMBER.permissions()).doesNotContainAnyElementsOf(
                Set.of(Permission.AUTOMATION_VIEW, Permission.RESOURCE_VIEW, Permission.AUDIT_VIEW));
    }

    @Test
    void onlyOwner_canManageBilling() {
        for (OrgRole role : OrgRole.values()) {
            assertThat(role.has(Permission.BILLING_MANAGE))
                    .as("BILLING_MANAGE for %s", role)
                    .isEqualTo(role == OrgRole.OWNER);
        }
    }

    @Test
    void onlyOwnerAndAdmin_canActivateAutomations() {
        for (OrgRole role : OrgRole.values()) {
            boolean expected = role == OrgRole.OWNER || role == OrgRole.ADMIN;
            assertThat(role.has(Permission.AUTOMATION_ACTIVATE))
                    .as("AUTOMATION_ACTIVATE for %s", role)
                    .isEqualTo(expected);
        }
    }

    @Test
    void onlyOwnerAndAdmin_canManageMembers() {
        for (OrgRole role : OrgRole.values()) {
            boolean expected = role == OrgRole.OWNER || role == OrgRole.ADMIN;
            assertThat(role.has(Permission.MEMBER_MANAGE))
                    .as("MEMBER_MANAGE for %s", role)
                    .isEqualTo(expected);
        }
    }
}
