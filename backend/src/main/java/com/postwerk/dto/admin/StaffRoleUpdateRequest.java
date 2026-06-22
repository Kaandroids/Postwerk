package com.postwerk.dto.admin;

/**
 * Request to assign a platform staff role to a user. A {@code null} or blank value clears the
 * staff role, revoking the user's access to the admin panel.
 */
public record StaffRoleUpdateRequest(
        String staffRole
) {}
