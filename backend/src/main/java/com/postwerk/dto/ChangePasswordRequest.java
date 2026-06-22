package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO for changing the authenticated user's password. */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
