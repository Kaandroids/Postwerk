package com.postwerk.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for blacklisting revoked JWT tokens using Redis with automatic TTL-based expiration.
 * Ensures that invalidated access tokens cannot be reused before their natural expiry.
 *
 * @since 1.0
 */
@Service
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklist(String jti, long remainingTtlMs) {
        if (remainingTtlMs > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + jti,
                    "blacklisted",
                    remainingTtlMs,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}
