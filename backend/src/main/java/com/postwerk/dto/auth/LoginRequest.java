package com.postwerk.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request DTO for user login with email and password. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
