package com.postwerk.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request DTO for new user registration with GDPR consent flags. */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 100) String company,
        @Size(max = 30) String phone,
        boolean marketingOptIn,
        @AssertTrue(message = "Terms and privacy policy must be accepted") boolean termsAccepted
) {}
