package com.postwerk.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request DTO for initiating a password reset by email. */
public record ResetPasswordRequest(
        @NotBlank @Email String email
) {}
