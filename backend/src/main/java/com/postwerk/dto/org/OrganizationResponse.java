package com.postwerk.dto.org;

import com.postwerk.model.enums.OrgRole;

import java.util.UUID;

/**
 * Summary of an organization the caller belongs to, including the caller's own role.
 * Used to populate the org switcher.
 *
 * @since 1.0
 */
public record OrganizationResponse(UUID id,
                                   String name,
                                   String slug,
                                   boolean personal,
                                   OrgRole myRole,
                                   long memberCount,
                                   String planName) {
}
