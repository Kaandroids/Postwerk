package com.postwerk.util;

import java.util.UUID;

/**
 * Lenient UUID parsing shared across services. Replaces the repeated private
 * {@code tryParseUuid} / {@code parseUuid} helpers that were duplicated in several classes.
 *
 * @since 1.0
 */
public final class UuidUtil {

    private UuidUtil() {}

    /**
     * Parses a UUID, returning {@code null} for {@code null}, blank, or malformed input.
     *
     * @param s the raw string (may be {@code null})
     * @return the parsed UUID, or {@code null}
     */
    public static UUID parseOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a UUID, throwing {@link IllegalArgumentException} with a {@code "Invalid <label>"}
     * message for {@code null}, blank, or malformed input.
     *
     * @param s     the raw string
     * @param label human-readable name for the error message (e.g. {@code "organization id"})
     * @return the parsed UUID, never {@code null}
     */
    public static UUID parseOrThrow(String s, String label) {
        if (s != null && !s.isBlank()) {
            try {
                return UUID.fromString(s.trim());
            } catch (IllegalArgumentException ignored) {
                // fall through to the uniform error below
            }
        }
        throw new IllegalArgumentException("Invalid " + label);
    }
}
