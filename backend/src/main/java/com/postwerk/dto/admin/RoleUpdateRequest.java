package com.postwerk.dto.admin;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for updating a user's role (USER/ADMIN). */
public record RoleUpdateRequest(
        @NotBlank String role
) {}
