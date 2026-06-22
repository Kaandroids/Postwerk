package com.postwerk.service;

import com.postwerk.config.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing opaque refresh tokens stored in Redis.
 * Supports token creation, validation, revocation, and bulk revocation per user.
 *
 * @since 1.0
 */
@Service
public class RefreshTokenService {

    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String USER_TOKENS_PREFIX = "auth:user-tokens:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    public String create(String email) {
        String token = UUID.randomUUID().toString();
        long ttlMs = jwtProperties.refreshTokenExpirationMs();

        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + token,
                email,
                ttlMs,
                TimeUnit.MILLISECONDS
        );
        // Track token in a per-user set for efficient bulk revocation
        redisTemplate.opsForSet().add(USER_TOKENS_PREFIX + email, token);
        redisTemplate.expire(USER_TOKENS_PREFIX + email, ttlMs, TimeUnit.MILLISECONDS);

        return token;
    }

    public String validate(String token) {
        return redisTemplate.opsForValue().get(REFRESH_PREFIX + token);
    }

    public void revoke(String token) {
        String email = redisTemplate.opsForValue().get(REFRESH_PREFIX + token);
        redisTemplate.delete(REFRESH_PREFIX + token);
        if (email != null) {
            redisTemplate.opsForSet().remove(USER_TOKENS_PREFIX + email, token);
        }
    }

    public void revokeAllForUser(String email) {
        var tokens = redisTemplate.opsForSet().members(USER_TOKENS_PREFIX + email);
        if (tokens != null) {
            for (String token : tokens) {
                redisTemplate.delete(REFRESH_PREFIX + token);
            }
        }
        redisTemplate.delete(USER_TOKENS_PREFIX + email);
    }

    /**
     * Number of <em>live</em> refresh tokens (active sessions) for a user. Refresh tokens that
     * expired naturally (TTL) are auto-removed from Redis but linger in the per-user set, so this
     * prunes those stale entries while counting — keeping the active-session count accurate rather
     * than over-reporting. Returns 0 if the user has no live tokens.
     *
     * @param email the user's email (the per-user token set key)
     * @return the active session count, never negative
     */
    public long countForUser(String email) {
        String setKey = USER_TOKENS_PREFIX + email;
        var tokens = redisTemplate.opsForSet().members(setKey);
        if (tokens == null || tokens.isEmpty()) {
            return 0L;
        }
        long live = 0L;
        for (String token : tokens) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_PREFIX + token))) {
                live++;
            } else {
                // Token has expired (TTL) but is still tracked in the set — prune it.
                redisTemplate.opsForSet().remove(setKey, token);
            }
        }
        return live;
    }
}
