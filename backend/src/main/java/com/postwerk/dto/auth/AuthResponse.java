package com.postwerk.dto.auth;

/** Response DTO containing JWT access/refresh tokens after authentication. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String role
) {}
