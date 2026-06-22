package com.postwerk.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates cryptographically strong, URL-safe random tokens.
 *
 * <p>Used for unguessable inbound webhook URL tokens and self-generated signing secrets.
 * Tokens are 32 bytes of {@link SecureRandom} entropy encoded as URL-safe base64 (no padding),
 * yielding ~43-character strings.</p>
 *
 * @since 1.0
 */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_BYTES = 32;

    private TokenGenerator() {
    }

    /** Generates a 32-byte URL-safe token (~43 chars). */
    public static String generate() {
        return generate(DEFAULT_BYTES);
    }

    /** Generates a URL-safe token from {@code numBytes} of entropy. */
    public static String generate(int numBytes) {
        byte[] bytes = new byte[numBytes];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
