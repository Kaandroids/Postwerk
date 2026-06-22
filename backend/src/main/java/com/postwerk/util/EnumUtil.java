package com.postwerk.util;

/**
 * Lenient enum parsing shared across services. Replaces the repeated
 * {@code try { Enum.valueOf(...) } catch (IllegalArgumentException) { ... }} blocks
 * that were duplicated in multiple service implementations.
 *
 * @since 1.0
 */
public final class EnumUtil {

    private EnumUtil() {}

    /**
     * Parses an enum constant leniently (trims + upper-cases the input), returning
     * {@code fallback} when the value is {@code null}, blank, or unrecognized.
     *
     * @param type     the enum class
     * @param value    the raw string (may be {@code null})
     * @param fallback the value to return when parsing fails
     * @param <E>      the enum type
     * @return the parsed constant, or {@code fallback}
     */
    public static <E extends Enum<E>> E parseOrDefault(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Parses an enum constant strictly (trims + upper-cases the input), throwing
     * {@link IllegalArgumentException} with a {@code "Invalid <label>: <value>"} message
     * when the value is {@code null}, blank, or unrecognized.
     *
     * @param type  the enum class
     * @param value the raw string
     * @param label human-readable name for the error message (e.g. {@code "audit action"})
     * @param <E>   the enum type
     * @return the parsed constant, never {@code null}
     */
    public static <E extends Enum<E>> E parseOrThrow(Class<E> type, String value, String label) {
        if (value != null && !value.isBlank()) {
            try {
                return Enum.valueOf(type, value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to the uniform error below
            }
        }
        throw new IllegalArgumentException("Invalid " + label + ": " + value);
    }
}
