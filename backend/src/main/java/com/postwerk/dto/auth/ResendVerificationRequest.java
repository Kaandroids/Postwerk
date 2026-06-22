package com.postwerk.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO to re-send the verification email to an unverified account. */
public record ResendVerificationRequest(
        @NotBlank @Email String email,
        /** Optional UI language ("de"/"en") for the verification email. Defaults to English. */
        @Size(max = 8) String lang
) {}
