package com.postwerk.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for refreshing an expired access token. */
public record RefreshRequest(
        @NotBlank String refreshToken
) {}
