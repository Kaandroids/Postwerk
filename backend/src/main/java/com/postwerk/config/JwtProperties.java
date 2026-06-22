package com.postwerk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration properties for JWT token generation and validation.
 *
 * <p>Bound from {@code app.jwt.*} in {@code application.yml}. Defines the HMAC signing
 * secret and expiration durations for both access and refresh tokens.</p>
 *
 * @since 1.0
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationMs,
        long refreshTokenExpirationMs
) {}
