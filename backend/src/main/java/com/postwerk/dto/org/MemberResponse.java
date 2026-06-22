package com.postwerk.dto.org;

import com.postwerk.model.enums.MembershipStatus;
import com.postwerk.model.enums.OrgRole;

import java.util.UUID;

/**
 * A member of an organization (membership joined with the user's identity).
 *
 * @since 1.0
 */
public record MemberResponse(UUID userId,
                             String email,
                             String fullName,
                             OrgRole role,
                             MembershipStatus status) {
}
