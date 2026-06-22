package com.postwerk.dto.org;

import com.postwerk.model.enums.OrgRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to add an existing registered user to an organization by email.
 * {@code role} defaults to MEMBER when null; OWNER cannot be assigned here.
 *
 * @since 1.0
 */
public record InviteMemberRequest(
        @NotBlank @Email String email,
        OrgRole role) {
}
