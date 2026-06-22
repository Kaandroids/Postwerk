package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotBlank;

/** Grant or change a staff member's role (the new role's enum name). */
public record SetStaffRoleRequest(
        @NotBlank String role
) {}
