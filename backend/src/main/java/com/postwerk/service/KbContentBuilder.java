package com.postwerk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

/**
 * Derives a knowledge-base entry's retrieval text from its {@code data} and the KB's
 * {@code fieldRoles} overlay: the {@code embed:true} fields form the semantic embedding text and the
 * {@code keyword:true} fields form the full-text {@code search_text}. Shared by
 * {@code KnowledgeBaseServiceImpl} (write path) and the async embedding worker (read path) so both
 * compute identical text (DRY).
 *
 * @since 1.0
 */
@Component
public class KbContentBuilder {

    private final ObjectMapper objectMapper;

    public KbContentBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Space-joined values of the {@code embed:true} fields — the text fed to the embedding model. */
    public String embedText(String fieldRolesJson, String dataJson) {
        return concatByRole(fieldRolesJson, dataJson, "embed");
    }

    /** Space-joined values of the {@code keyword:true} fields — backs the GIN full-text index. */
    public String searchText(String fieldRolesJson, String dataJson) {
        return concatByRole(fieldRolesJson, dataJson, "keyword");
    }

    /** SHA-256 hex of the given text — lets re-import skip re-embedding unchanged rows. */
    public String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String concatByRole(String fieldRolesJson, String dataJson, String role) {
        try {
            JsonNode roles = objectMapper.readTree(blankToEmptyObject(fieldRolesJson));
            JsonNode data = objectMapper.readTree(blankToEmptyObject(dataJson));
            StringBuilder sb = new StringBuilder();
            Iterator<String> fields = roles.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (roles.get(field).path(role).asBoolean(false)) {
                    JsonNode value = data.get(field);
                    if (value != null && !value.isNull()) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(value.asText());
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String blankToEmptyObject(String json) {
        return (json == null || json.isBlank()) ? "{}" : json;
    }
}
