package com.postwerk.dto.org;

import com.postwerk.model.enums.OrgRole;

import java.util.UUID;

/**
 * A pending invitation for the caller to join an organization (multi-tenant model #4). Surfaced in
 * the org switcher so the invitee can accept (membership → ACTIVE) or decline (membership removed).
 *
 * @since 1.0
 */
public record InvitationResponse(UUID organizationId,
                                 String organizationName,
                                 OrgRole role,
                                 String invitedByName) {
}
