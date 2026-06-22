package com.postwerk.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Platform-wide maintenance-mode flag, backed by Redis so it is shared across instances and survives
 * restarts. This is currently <strong>metadata-only</strong>: the flag is stored, read and audited,
 * but request blocking / a customer maintenance page is NOT yet wired (a future enforcement step,
 * mirroring the product's metadata-only billing). Toggled by platform staff from System Health.
 *
 * @since 1.0
 */
@Service
public class MaintenanceModeService {

    private static final String K_ENABLED = "system:maintenance:enabled";
    private static final String K_MESSAGE = "system:maintenance:message";
    private static final String K_UPDATED = "system:maintenance:updated_at";

    private final StringRedisTemplate redis;

    public MaintenanceModeService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isEnabled() {
        try {
            return "true".equals(redis.opsForValue().get(K_ENABLED));
        } catch (Exception e) {
            return false; // Redis unavailable → fail open (no maintenance).
        }
    }

    public String message() {
        try {
            return redis.opsForValue().get(K_MESSAGE);
        } catch (Exception e) {
            return null;
        }
    }

    public Instant updatedAt() {
        try {
            String v = redis.opsForValue().get(K_UPDATED);
            return v == null ? null : Instant.parse(v);
        } catch (Exception e) {
            return null;
        }
    }

    public void set(boolean enabled, String message) {
        redis.opsForValue().set(K_ENABLED, Boolean.toString(enabled));
        if (message == null || message.isBlank()) {
            redis.delete(K_MESSAGE);
        } else {
            redis.opsForValue().set(K_MESSAGE, message.trim());
        }
        redis.opsForValue().set(K_UPDATED, Instant.now().toString());
    }
}
