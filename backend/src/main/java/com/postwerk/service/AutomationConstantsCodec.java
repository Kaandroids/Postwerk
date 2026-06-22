package com.postwerk.service;

import com.postwerk.config.EncryptionConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared codec for per-automation user-defined constants. Centralizes the constants JSON shape
 * ({@code {"NAME":{"value":...,"type":...,"desc":...}}}, with a legacy flat {@code {"NAME":"value"}}
 * fallback), the allowed type set and name validation, secret encryption/decryption, and runtime
 * {@code const.*} variable resolution.
 *
 * <p>Used by automation CRUD, the marketplace publish/configure flows, and the execution engine so
 * that all three agree on the storage format and secret handling.</p>
 *
 * @since 1.0
 */
@Component
public class AutomationConstantsCodec {

    private static final Logger log = LoggerFactory.getLogger(AutomationConstantsCodec.class);

    /** Allowed characters for a constant name: letters, digits, underscore. */
    public static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    /** Supported constant types. */
    public static final Set<String> TYPES = Set.of("text", "number", "boolean", "url", "secret");

    public static final String SECRET_TYPE = "secret";
    public static final String TEXT_TYPE = "text";

    private final ObjectMapper objectMapper;
    private final EncryptionConfig encryption;

    public AutomationConstantsCodec(ObjectMapper objectMapper, EncryptionConfig encryption) {
        this.objectMapper = objectMapper;
        this.encryption = encryption;
    }

    /** Whether {@code name} is a valid constant name. */
    public boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /** Normalizes a type to a supported value, defaulting to {@code text}. */
    public String normalizeType(String type) {
        if (type == null) return TEXT_TYPE;
        String t = type.trim().toLowerCase();
        return TYPES.contains(t) ? t : TEXT_TYPE;
    }

    /** Raw stored type of a constant node (un-normalized), defaulting to {@code text}. */
    public String rawTypeOf(JsonNode node) {
        if (node == null) return TEXT_TYPE;
        return node.isObject() ? node.path("type").asText(TEXT_TYPE) : TEXT_TYPE;
    }

    /** Extracts the stored (possibly encrypted) raw value from a constant node, handling both forms. */
    public String storedValue(JsonNode node) {
        if (node == null) return null;
        return node.isObject() ? node.path("value").asText("") : node.asText("");
    }

    /** Encrypts a plaintext secret value for storage at rest. */
    public String encryptSecret(String plain) {
        return encryption.encrypt(plain);
    }

    /**
     * Builds a stored constant entry ({@code {"type":...,"desc":...,"value":...}}) applying the shared
     * secret-handling rule: {@code secret}-typed values are encrypted at rest, and a blank incoming
     * secret value preserves the previously stored secret (read from {@code previousNode}).
     *
     * @param type          the (already normalized/raw) constant type
     * @param desc          the constant description (may be {@code null})
     * @param incomingValue the incoming value (plaintext for secrets; may be {@code null}/blank)
     * @param previousNode  the previously stored node for this name, or {@code null} if new
     * @return the entry node
     */
    public ObjectNode writeEntry(String type, String desc, String incomingValue, JsonNode previousNode) {
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("type", type);
        entry.put("desc", desc == null ? "" : desc);
        if (SECRET_TYPE.equals(type)) {
            if (incomingValue == null || incomingValue.isBlank()) {
                String prev = storedValue(previousNode);
                entry.put("value", prev == null ? "" : prev);
            } else {
                entry.put("value", encryptSecret(incomingValue));
            }
        } else {
            entry.put("value", incomingValue == null ? "" : incomingValue);
        }
        return entry;
    }

    /**
     * Reads a constants JSON object into an ordered map of name → raw value node.
     * Returns an empty map for {@code null}/blank/invalid JSON.
     */
    public Map<String, JsonNode> readNodes(String json) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isObject()) root.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue()));
        } catch (Exception e) {
            log.warn("Failed to parse automation constants: {}", e.getMessage());
        }
        return map;
    }

    /**
     * Exposes the constants as {@code const.NAME} runtime variables for {@code {{const.NAME}}}
     * placeholder resolution, decrypting {@code secret}-typed values.
     */
    public Map<String, Object> toRuntimeVars(String json) {
        Map<String, JsonNode> nodes = readNodes(json);
        if (nodes.isEmpty()) return Map.of();
        Map<String, Object> vars = new HashMap<>();
        nodes.forEach((name, node) -> vars.put("const." + name, resolveRuntimeValue(node)));
        return vars;
    }

    /**
     * Resolves a stored constant node to its runtime string value, decrypting {@code secret}-typed
     * values. Supports both the legacy flat form and the typed {@code {value,type}} form.
     */
    public String resolveRuntimeValue(JsonNode node) {
        if (node == null || !node.isObject()) return node == null ? "" : node.asText("");
        String value = node.path("value").asText("");
        if (SECRET_TYPE.equals(node.path("type").asText(TEXT_TYPE))) {
            if (value.isBlank()) return "";
            try {
                return encryption.decrypt(value);
            } catch (Exception e) {
                log.warn("Failed to decrypt secret constant: {}", e.getMessage());
                return "";
            }
        }
        return value;
    }
}
