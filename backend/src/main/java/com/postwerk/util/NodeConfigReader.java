package com.postwerk.util;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Null-safe readers for {@link JsonNode}-based node configuration fields.
 *
 * <p>Centralizes the repeated {@code config.has(field) ? config.get(field).asX() : default}
 * pattern used across automation node executors. Behavior matches the prior inline
 * expressions exactly: when the field is absent the supplied default is returned,
 * otherwise the present node's {@code asText()/asInt()/asLong()/asBoolean()} value is used.</p>
 *
 * @since 1.0
 */
public final class NodeConfigReader {

    private NodeConfigReader() {
    }

    /** Returns the field's text value, or {@code def} if the field is absent. */
    public static String text(JsonNode config, String field, String def) {
        return config != null && config.has(field) ? config.get(field).asText() : def;
    }

    /** Returns the field's text value, or {@code ""} if the field is absent. */
    public static String text(JsonNode config, String field) {
        return text(config, field, "");
    }

    /** Returns the field's int value, or {@code def} if the field is absent. */
    public static int integer(JsonNode config, String field, int def) {
        return config != null && config.has(field) ? config.get(field).asInt(def) : def;
    }

    /** Returns the field's long value, or {@code def} if the field is absent. */
    public static long longValue(JsonNode config, String field, long def) {
        return config != null && config.has(field) ? config.get(field).asLong(def) : def;
    }

    /** Returns the field's boolean value, or {@code def} if the field is absent. */
    public static boolean bool(JsonNode config, String field, boolean def) {
        return config != null && config.has(field) ? config.get(field).asBoolean(def) : def;
    }
}
