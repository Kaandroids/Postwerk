package com.postwerk.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Single-use, time-limited tokens for email verification and password reset, stored in Redis
 * (mirrors {@link RefreshTokenService}). Tokens map {@code token -> userId} and are consumed
 * atomically (read-and-delete) so a link works exactly once.
 *
 * <p>Also tracks a short per-email cooldown to throttle "resend verification" requests.</p>
 */
@Service
public class VerificationTokenService {

    private static final String VERIFY_PREFIX = "auth:verify:";
    private static final String RESET_PREFIX = "auth:reset:";
    private static final String RESEND_COOLDOWN_PREFIX = "auth:verify-cooldown:";

    /** Verification links are valid for 24h; reset links for 1h (kept shorter — higher-risk action). */
    public static final Duration VERIFY_TTL = Duration.ofHours(24);
    public static final Duration RESET_TTL = Duration.ofHours(1);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public VerificationTokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String createVerificationToken(UUID userId) {
        return create(VERIFY_PREFIX, userId, VERIFY_TTL);
    }

    /** @return the userId the token was issued for, or {@code null} if invalid/expired. Consumes the token. */
    public UUID consumeVerificationToken(String token) {
        return consume(VERIFY_PREFIX, token);
    }

    public String createResetToken(UUID userId) {
        return create(RESET_PREFIX, userId, RESET_TTL);
    }

    /** @return the userId the token was issued for, or {@code null} if invalid/expired. Consumes the token. */
    public UUID consumeResetToken(String token) {
        return consume(RESET_PREFIX, token);
    }

    /** True if a verification email was sent to this address within the cooldown window. */
    public boolean isResendOnCooldown(String email) {
        return Boolean.TRUE.equals(redis.hasKey(RESEND_COOLDOWN_PREFIX + key(email)));
    }

    /** Records that a verification email was just sent, starting the resend cooldown. */
    public void markResendSent(String email) {
        redis.opsForValue().set(RESEND_COOLDOWN_PREFIX + key(email), "1", RESEND_COOLDOWN);
    }

    private String create(String prefix, UUID userId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(prefix + token, userId.toString(), ttl);
        return token;
    }

    private UUID consume(String prefix, String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String userId = redis.opsForValue().getAndDelete(prefix + token);
        if (userId == null) {
            return null;
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
