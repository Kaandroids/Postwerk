package com.postwerk.dto;

/**
 * A single user-defined automation constant, referenced inside nodes as {@code {{const.NAME}}}.
 *
 * @param name        the constant key (UPPER_SNAKE, letters/digits/underscore)
 * @param value       the constant value. For {@code secret} constants this is never the plaintext on
 *                    responses (it is {@code null} and {@link #hasValue} signals presence); on requests a
 *                    {@code null}/blank value means "keep the previously stored secret".
 * @param type        the value type: {@code text | number | boolean | url | secret}
 * @param hasValue    response-only flag indicating a secret has a stored value (always false for requests)
 * @param description optional human description of what the constant is for
 */
public record AutomationConstantDto(
        String name,
        String value,
        String type,
        boolean hasValue,
        String description
) {
    /** Convenience constructor for non-secret constants (hasValue defaults to false, no description). */
    public AutomationConstantDto(String name, String value, String type) {
        this(name, value, type, false, null);
    }
}
