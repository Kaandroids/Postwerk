package com.postwerk.dto.org;

import com.postwerk.model.enums.OrgRole;

import java.util.List;
import java.util.UUID;

/**
 * Full organization view: identity, the caller's role, and the member roster.
 *
 * @since 1.0
 */
public record OrganizationDetailResponse(UUID id,
                                         String name,
                                         String slug,
                                         boolean personal,
                                         OrgRole myRole,
                                         String planName,
                                         List<MemberResponse> members) {
}
