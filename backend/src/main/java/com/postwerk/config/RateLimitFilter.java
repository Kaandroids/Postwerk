package com.postwerk.config;

import com.postwerk.util.IpResolverUtil;
import com.postwerk.util.WebhookConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Redis-backed rate limiting filter for critical API endpoints.
 *
 * <p>Applies per-IP sliding window rate limits to prevent brute-force attacks,
 * AI abuse, and sync flooding. Each endpoint group has its own limit configuration.
 * Uses Redis INCR + EXPIRE for atomic, distributed rate counting.</p>
 *
 * <p>Rate limit groups:
 * <ul>
 *   <li><b>auth</b> — login, register, refresh: 20 requests/minute per IP</li>
 *   <li><b>ai</b> — AI chat endpoint: 30 requests/minute per IP</li>
 *   <li><b>sync</b> — email sync: 10 requests/minute per IP</li>
 *   <li><b>email-send</b> — outbound email send: 50 requests/hour per IP</li>
 * </ul>
 *
 * @since 1.0
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String RATE_LIMIT_PREFIX = "rate:";

    private final StringRedisTemplate redisTemplate;

    /** Endpoint prefix → {maxRequests, windowSeconds}. */
    private static final Map<String, int[]> RATE_LIMITS = Map.of(
            "/api/v1/auth/login",          new int[]{20, 60},
            "/api/v1/auth/register",       new int[]{10, 60},
            "/api/v1/auth/refresh",        new int[]{30, 60},
            "/api/v1/auth/reset-password", new int[]{5,  60},
            "/api/v1/ai/",                 new int[]{30, 60},
            "/api/v1/wizard/chat",         new int[]{5,  60},
            WebhookConstants.HOOKS_PATH_PREFIX, new int[]{120, 60}
    );

    /** Suffix-based limits for password change. */
    private static final String PASSWORD_SUFFIX = "/change-password";
    private static final int[] PASSWORD_LIMIT = {5, 60};

    /** Suffix-based limits (matched by endsWith). */
    private static final String SYNC_SUFFIX = "/emails/sync";
    private static final int[] SYNC_LIMIT = {10, 60};

    /** Suffix-based limit for outbound email sending (abuse / spam relay protection): 50/hour. */
    private static final String SEND_SUFFIX = "/emails/send";
    private static final int[] SEND_LIMIT = {50, 3600};

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        int[] limit = resolveLimit(path);

        if (limit != null) {
            String ip = extractIp(request);
            String group = resolveGroup(path);
            String key = RATE_LIMIT_PREFIX + group + ":" + ip;

            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(limit[1]));
            }

            // Add rate limit headers
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit[0]));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit[0] - (count != null ? count : 0))));

            if (count != null && count > limit[0]) {
                log.warn("Rate limit exceeded for IP {} on {}: {}/{}", ip, group, count, limit[0]);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again later.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private int[] resolveLimit(String path) {
        for (var entry : RATE_LIMITS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        if (path.endsWith(SYNC_SUFFIX) && path.contains("/api/v1/email-accounts/")) {
            return SYNC_LIMIT;
        }
        if (path.endsWith(SEND_SUFFIX) && path.contains("/api/v1/email-accounts/")) {
            return SEND_LIMIT;
        }
        if (path.endsWith(PASSWORD_SUFFIX) && path.contains("/api/v1/users/")) {
            return PASSWORD_LIMIT;
        }
        return null;
    }

    private String resolveGroup(String path) {
        if (path.startsWith("/api/v1/auth/login")) return "auth-login";
        if (path.startsWith("/api/v1/auth/register")) return "auth-register";
        if (path.startsWith("/api/v1/auth/refresh")) return "auth-refresh";
        if (path.startsWith("/api/v1/auth/reset-password")) return "auth-reset-password";
        if (path.startsWith("/api/v1/ai/")) return "ai-chat";
        if (path.startsWith("/api/v1/wizard/chat")) return "wizard-chat";
        if (path.startsWith(WebhookConstants.HOOKS_PATH_PREFIX)) return "webhook-ingress";
        if (path.endsWith(SYNC_SUFFIX)) return "email-sync";
        if (path.endsWith(SEND_SUFFIX)) return "email-send";
        if (path.endsWith(PASSWORD_SUFFIX)) return "change-password";
        return "default";
    }

    private String extractIp(HttpServletRequest request) {
        return IpResolverUtil.extractIp(request);
    }
}
