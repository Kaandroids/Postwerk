package com.postwerk.util;

/**
 * Small null-safe string helpers shared across services and node executors.
 *
 * @since 1.0
 */
public final class SafeStrings {

    private SafeStrings() {
    }

    /** Returns the given string, or {@code ""} when {@code null}. */
    public static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    /** Strips CR/LF characters to prevent email header injection. Null-safe. */
    public static String stripCrlf(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\r\\n]", "");
    }

    /**
     * Case-insensitive substring test used by the in-memory admin search filters. Returns
     * {@code false} when either argument is {@code null}. Replaces the identical private
     * {@code contains(...)} helper duplicated across the admin services.
     */
    public static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null
                && haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
