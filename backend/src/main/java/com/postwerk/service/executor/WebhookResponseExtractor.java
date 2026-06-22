package com.postwerk.service.executor;

import com.postwerk.model.ParameterSet;
import com.postwerk.repository.ParameterSetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Extracts named response fields from a WEBHOOK/HTTP node's JSON response according to the node's
 * declared response ParameterSet(s) — "Model B": ParameterSet-driven and deterministic.
 *
 * <p>The node config's {@code responseSchemas} map an HTTP-status condition (e.g. {@code 2xx},
 * {@code 4xx}, {@code 404}, or {@code *}) to a ParameterSet; the first schema whose condition matches
 * the actual status is used. The (recursively nested) ParameterSet tree is walked against the response
 * JSON, producing flat dot/bracket-notation variables (e.g. {@code order.customer.email},
 * {@code items[0].sku}, {@code items.length}) — and <b>only</b> the declared fields.</p>
 *
 * <p>When no ParameterSet is declared or matches, nothing is extracted: the caller still exposes the
 * raw {@code body} + {@code statusCode}, and users who need ad-hoc fields run an EXTRACT node on
 * {@code body}.</p>
 *
 * @since 1.0
 */
@Component
public class WebhookResponseExtractor {

    private static final Logger log = LoggerFactory.getLogger(WebhookResponseExtractor.class);
    /** Hard cap on extracted leaf fields (bounds list expansion / hostile payloads). */
    private static final int MAX_FIELDS = 500;
    /** Max array elements walked per list field. */
    private static final int MAX_ARRAY_ELEMENTS = 100;

    private final ObjectMapper objectMapper;
    private final ParameterSetRepository parameterSetRepository;

    public WebhookResponseExtractor(ObjectMapper objectMapper, ParameterSetRepository parameterSetRepository) {
        this.objectMapper = objectMapper;
        this.parameterSetRepository = parameterSetRepository;
    }

    /** The {@code condition} sentinel marking the pinned catch-all branch (routes to the {@code unmatched} handle). */
    public static final String UNMATCHED_CONDITION = "unmatched";

    /**
     * The routing decision for a webhook response: which output handle to follow and which
     * ParameterSet (if any) to parse the response body with. Each non-{@code unmatched} schema in
     * declaration order is a branch with handle {@code resp_<arrayIndex>}; when none match, the
     * response routes to the {@code unmatched} handle (using the pinned unmatched schema's
     * ParameterSet, if one was declared).
     *
     * @param handle         the output handle to activate ({@code resp_<i>} or {@code unmatched})
     * @param parameterSetId the ParameterSet to extract the body with, or {@code null} for none
     */
    public record Match(String handle, UUID parameterSetId) {}

    /**
     * Decides the output handle + extraction ParameterSet for a response status. The <b>most
     * specific</b> matching non-{@code unmatched} schema wins (more fixed digits in a digit/{@code x}
     * pattern rank higher: {@code 210} &gt; {@code 21x} &gt; {@code 2xx} &gt; {@code *}); ties break by
     * declaration order. When nothing matches, routes to {@code unmatched} (carrying the pinned
     * unmatched schema's ParameterSet, if present).
     */
    public Match match(JsonNode responseSchemas, int statusCode) {
        String bestHandle = "unmatched";
        UUID bestPs = null;
        int bestSpecificity = -1;
        UUID unmatchedPs = null;
        if (responseSchemas != null && responseSchemas.isArray()) {
            int idx = -1;
            for (JsonNode schema : responseSchemas) {
                idx++;
                String condition = schema.path("condition").asText("");
                if (UNMATCHED_CONDITION.equalsIgnoreCase(condition.trim())) {
                    unmatchedPs = tryParseUuid(schema.path("parameterSetId").asText(""));
                    continue;
                }
                int specificity = conditionSpecificity(condition, statusCode);
                if (specificity > bestSpecificity) {
                    bestSpecificity = specificity;
                    bestHandle = "resp_" + idx;
                    bestPs = tryParseUuid(schema.path("parameterSetId").asText(""));
                }
            }
        }
        if (bestSpecificity < 0) {
            return new Match("unmatched", unmatchedPs);
        }
        return new Match(bestHandle, bestPs);
    }

    /**
     * Extracts the declared response fields using the schema matched for {@code statusCode}. Returns an
     * empty map (never {@code null}) when no schema matches, the matched schema has no ParameterSet, the
     * body is not JSON, or anything fails. Routing and extraction share {@link #match} so the parsed
     * fields always belong to the branch the response is routed to.
     *
     * @param responseSchemas the node config's {@code responseSchemas} array (may be {@code null})
     * @param statusCode      the actual HTTP status of the response
     * @param body            the raw response body
     * @param organizationId  owning org of the running automation (ParameterSet scoping key)
     */
    public Map<String, Object> extract(JsonNode responseSchemas, int statusCode, String body, UUID organizationId) {
        return extractWithParameterSet(match(responseSchemas, statusCode).parameterSetId(), body, organizationId);
    }

    /**
     * Extracts the declared fields of {@code parameterSetId} from the JSON {@code body}. Returns an
     * empty map (never {@code null}) when the ParameterSet is absent, the body is not JSON, or anything
     * fails. The non-JSON check runs before the repository lookup, so a non-JSON body never hits the DB.
     */
    public Map<String, Object> extractWithParameterSet(UUID parameterSetId, String body, UUID organizationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (parameterSetId == null || body == null || organizationId == null) {
            return out;
        }
        String trimmed = body.stripLeading();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return out; // not JSON — only the raw body is available
        }
        ParameterSet ps = parameterSetRepository.findByIdAndOrganizationId(parameterSetId, organizationId).orElse(null);
        if (ps == null) {
            return out;
        }
        try {
            JsonNode params = objectMapper.readTree(ps.getParameters());
            JsonNode response = objectMapper.readTree(body);
            extractFields("", params, response, out);
        } catch (Exception e) {
            log.warn("Webhook response extraction failed for parameter set {}: {}",
                    parameterSetId, e.getMessage());
        }
        return out;
    }

    /**
     * Specificity of a status condition against an actual status. A condition is a digit/{@code x}
     * pattern the same length as the status (always 3 for HTTP): each {@code x} is a single-digit
     * wildcard, each digit must match that position. Specificity = the number of fixed (non-{@code x})
     * digits, so {@code 210} (3) &gt; {@code 21x} (2) &gt; {@code 2xx} (1) &gt; {@code *}/{@code any}/empty
     * (0). Returns {@code -1} when the pattern does not match.
     */
    private int conditionSpecificity(String condition, int statusCode) {
        String c = condition == null ? "" : condition.trim().toLowerCase();
        if (c.isEmpty() || c.equals("*") || c.equals("any")) {
            return 0;
        }
        String status = Integer.toString(statusCode);
        if (c.length() != status.length()) {
            return -1;
        }
        int fixed = 0;
        for (int i = 0; i < c.length(); i++) {
            char pc = c.charAt(i);
            if (pc == 'x') {
                continue;
            }
            if (pc != status.charAt(i)) {
                return -1;
            }
            fixed++;
        }
        return fixed;
    }

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Walks the (possibly nested) ParameterSet definition against the response JSON, writing each
     * declared scalar leaf as a flat dot/bracket key. Objects descend via {@code .child}; list fields
     * ({@code isList}) iterate elements via {@code [i]} (plus a {@code .length} entry). Fields not
     * present in the response are skipped.
     */
    private void extractFields(String prefix, JsonNode params, JsonNode json, Map<String, Object> out) {
        if (params == null || !params.isArray() || json == null || !json.isObject()) {
            return;
        }
        for (JsonNode param : params) {
            if (out.size() >= MAX_FIELDS) {
                return;
            }
            String name = param.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }
            JsonNode value = json.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            String key = prefix.isEmpty() ? name : prefix + "." + name;
            JsonNode children = param.get("children");
            boolean hasChildren = children != null && children.isArray() && !children.isEmpty();

            if (param.path("isList").asBoolean(false) && value.isArray()) {
                int limit = Math.min(value.size(), MAX_ARRAY_ELEMENTS);
                for (int i = 0; i < limit && out.size() < MAX_FIELDS; i++) {
                    JsonNode elem = value.get(i);
                    if (hasChildren) {
                        extractFields(key + "[" + i + "]", children, elem, out);
                    } else if (elem != null && elem.isValueNode()) {
                        out.put(key + "[" + i + "]", elem.asText());
                    }
                }
                out.put(key + ".length", value.size());
            } else if (hasChildren) {
                extractFields(key, children, value, out);
            } else if (value.isValueNode()) {
                out.put(key, value.asText());
            }
        }
    }
}
