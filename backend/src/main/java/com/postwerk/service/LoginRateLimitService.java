package com.postwerk.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed rate limiter for login attempts that prevents brute-force attacks.
 * Tracks failed attempts per IP/email combination with a configurable sliding window and lockout threshold.
 *
 * @since 1.0
 */
@Service
public class LoginRateLimitService {

    private static final String RATE_LIMIT_PREFIX = "auth:login_attempts:";
    private static final int MAX_ATTEMPTS = 5;
    private static final int WINDOW_MINUTES = 15;

    private final StringRedisTemplate redisTemplate;

    public LoginRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if the given key (IP or email) is locked out.
     */
    public boolean isLockedOut(String key) {
        String val = redisTemplate.opsForValue().get(RATE_LIMIT_PREFIX + key);
        if (val == null) return false;
        return Integer.parseInt(val) >= MAX_ATTEMPTS;
    }

    /**
     * Record a failed attempt. Returns the new count.
     */
    public int recordFailedAttempt(String key) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        return count != null ? count.intValue() : 1;
    }

    /**
     * Clear failed attempts on successful login.
     */
    public void clearAttempts(String key) {
        redisTemplate.delete(RATE_LIMIT_PREFIX + key);
    }

    /**
     * Get remaining lockout seconds for a key.
     */
    public long getRemainingLockoutSeconds(String key) {
        Long ttl = redisTemplate.getExpire(RATE_LIMIT_PREFIX + key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    public int getMaxAttempts() {
        return MAX_ATTEMPTS;
    }

    public int getWindowMinutes() {
        return WINDOW_MINUTES;
    }
}
