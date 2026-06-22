package com.postwerk.util;

/**
 * Shared constants and helpers for inbound webhook endpoints, centralizing the public hooks
 * path prefix and the default API-key header name used across the security filter chain,
 * rate limiter, ingress service, and endpoint management service.
 *
 * @since 1.0
 */
public final class WebhookConstants {

    private WebhookConstants() {
    }

    /** Default header carrying the API key for {@code API_KEY} auth mode. */
    public static final String DEFAULT_AUTH_HEADER = "X-API-Key";

    /** Header carrying the HMAC-SHA256 signature for {@code HMAC} auth mode. */
    public static final String SIGNATURE_HEADER = "X-Postwerk-Signature";

    /** Base path (no trailing slash) for the inbound webhook controller mapping. */
    public static final String HOOKS_BASE_PATH = "/api/v1/hooks";

    /** Public path prefix (with trailing slash) for building per-token webhook URLs. */
    public static final String HOOKS_PATH_PREFIX = HOOKS_BASE_PATH + "/";

    /**
     * Returns the configured header name when non-blank, otherwise {@link #DEFAULT_AUTH_HEADER}.
     */
    public static String resolveAuthHeader(String configured) {
        return (configured != null && !configured.isBlank()) ? configured : DEFAULT_AUTH_HEADER;
    }
}
