package com.postwerk.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO to complete a password reset: the emailed token plus the new password. */
public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 64) String token,
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
