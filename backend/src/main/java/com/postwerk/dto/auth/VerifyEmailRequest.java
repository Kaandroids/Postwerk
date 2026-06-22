package com.postwerk.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO carrying the email-verification token from the link the user clicked. */
public record VerifyEmailRequest(
        @NotBlank @Size(max = 64) String token
) {}
