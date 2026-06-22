package com.postwerk.service;

import com.postwerk.model.enums.OrgRole;
import com.postwerk.model.enums.Permission;

import java.util.Set;
import java.util.UUID;

/**
 * Immutable, request-scoped snapshot of the caller's authority within the active organization
 * (multi-tenant model #4). Resolved by {@link OrgContextService} from the authenticated user and the
 * {@code X-Org-Id} header. Carries the org id, the caller's role, the role's permission bundle, and
 * whether the role bypasses per-mailbox grants (Owner/Admin).
 *
 * @since 1.0
 */
public record OrgContext(UUID organizationId,
                         UUID userId,
                         UUID membershipId,
                         OrgRole role,
                         Set<Permission> permissions,
                         boolean allMailboxAccess) {

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    public boolean isOwner() {
        return role == OrgRole.OWNER;
    }

    public boolean isAdminOrAbove() {
        return role == OrgRole.OWNER || role == OrgRole.ADMIN;
    }
}
