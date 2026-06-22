package com.postwerk.dto.org;

import com.postwerk.model.enums.OrgRole;
import jakarta.validation.constraints.NotNull;

/**
 * Request to change a member's role (OWNER cannot be assigned here — ownership transfer is separate).
 *
 * @since 1.0
 */
public record UpdateMemberRoleRequest(
        @NotNull OrgRole role) {
}
