package com.postwerk.service;

import com.postwerk.dto.automation.AutomationValidationResult;
import com.postwerk.dto.automation.ValidationIssue;
import com.postwerk.model.enums.AutomationKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared semantic "linter" for automation flows. Implements the core rule catalog
 * (see {@code doc} / frontend {@code AutomationLintService}) that both the backend enforcement
 * gate and the frontend live-lint agree on via a shared issue-<b>code</b> vocabulary.
 *
 * <p>This is the authoritative enforcement implementation: it blocks <b>activate</b> and
 * <b>publish</b> (any {@code error}-severity issue) and feeds AI self-correction. It checks
 * per-node required config, orphaned nodes, missing triggers, and dangling variable references.
 * Save is never gated — work-in-progress flows are always persistable.</p>
 *
 * @since 1.0
 */
@Component
public class AutomationValidator {

    private static final Logger log = LoggerFactory.getLogger(AutomationValidator.class);

    /** Matches {@code {{ token }}} placeholders in free-text config fields. */
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");

    /** Namespaces that are always considered resolvable (avoids dangling false-positives). */
    private static final Set<String> ALWAYS_ALLOWED_NS = Set.of("const");

    private final ObjectMapper objectMapper;

    public AutomationValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Minimal structural view of a flow node needed for validation. */
    public record NodeView(String id, String nodeType, String label, String config, String nodeKey) {}

    /** The node-scoped variable key (friendly nodeKey, falling back to the raw id if absent). */
    private static String nsKey(NodeView n) {
        return (n.nodeKey() != null && !n.nodeKey().isBlank()) ? n.nodeKey() : n.id();
    }

    /** Minimal structural view of a directed edge needed for validation. */
    public record EdgeView(String sourceId, String sourceHandle, String targetId, String targetHandle) {}

    /**
     * Runs the core rule catalog against a flow.
     *
     * @param kind          discriminates AUTOMATION (needs a TRIGGER) vs INTEGRATION (uses INPUT)
     * @param nodes         all flow nodes
     * @param edges         all flow edges
     * @param constantNames defined automation constant names (for {@code const.*} resolution)
     */
    public AutomationValidationResult validate(AutomationKind kind, List<NodeView> nodes,
                                               List<EdgeView> edges, Set<String> constantNames) {
        List<ValidationIssue> issues = new ArrayList<>();
        Set<String> constants = constantNames != null ? constantNames : Set.of();

        // ── MISSING_TRIGGER ───────────────────────────────────────────────
        if (kind != AutomationKind.INTEGRATION
                && nodes.stream().noneMatch(n -> "TRIGGER".equals(n.nodeType()))) {
            issues.add(ValidationIssue.error("MISSING_TRIGGER", null,
                    "This automation has no trigger node, so it can never start."));
        }

        Set<String> nodesWithIncoming = new HashSet<>();
        for (EdgeView e : edges) {
            nodesWithIncoming.add(e.targetId());
        }

        for (NodeView node : nodes) {
            JsonNode cfg = parse(node.config());
            String type = node.nodeType();

            // ── ORPHAN_NODE ───────────────────────────────────────────────
            if (!"TRIGGER".equals(type) && !"INPUT".equals(type)
                    && !nodesWithIncoming.contains(node.id())) {
                issues.add(ValidationIssue.warning("ORPHAN_NODE", node.id(),
                        "Node '" + label(node) + "' has no incoming connection and will never run."));
            }

            switch (type) {
                case "EXTRACT" -> {
                    JsonNode extractions = cfg.get("extractions");
                    boolean empty = extractions == null || !extractions.isArray() || extractions.isEmpty();
                    boolean missingRef = !empty && stream(extractions)
                            .anyMatch(ex -> isBlank(ex.path("parameterSetId").asText("")));
                    if (empty || missingRef) {
                        issues.add(ValidationIssue.error("EXTRACT_NO_PARAMSET", node.id(),
                                "Extract node '" + label(node) + "' has no parameter set selected."));
                    }
                }
                case "CATEGORIZE" -> {
                    JsonNode cats = cfg.get("categoryIds");
                    if (cats == null || !cats.isArray() || cats.isEmpty()) {
                        issues.add(ValidationIssue.error("CATEGORIZE_NO_CATEGORIES", node.id(),
                                "Categorize node '" + label(node) + "' has no categories selected."));
                    }
                }
                case "LABEL", "REMOVE_LABEL" -> {
                    if (isBlank(cfg.path("categoryId").asText(""))) {
                        issues.add(ValidationIssue.error("LABEL_NO_CATEGORY", node.id(),
                                "Label node '" + label(node) + "' has no category selected."));
                    }
                }
                case "SEND_EMAIL" -> {
                    if (isBlank(cfg.path("to").asText(""))) {
                        issues.add(ValidationIssue.error("SEND_NO_RECIPIENT", node.id(),
                                "Send-email node '" + label(node) + "' has no recipient address."));
                    }
                }
                case "EMAIL_ACTION" -> validateEmailAction(node, cfg, issues);
                case "WEBHOOK" -> {
                    if (isBlank(cfg.path("url").asText(""))) {
                        issues.add(ValidationIssue.error("WEBHOOK_NO_URL", node.id(),
                                "Webhook node '" + label(node) + "' has no URL."));
                    }
                }
                case "INTEGRATION_CALL" -> {
                    if (isBlank(cfg.path("integrationId").asText(""))) {
                        issues.add(ValidationIssue.error("INTEGRATION_NO_REF", node.id(),
                                "Integration-call node '" + label(node) + "' references no integration."));
                    }
                }
                case "INPUT", "OUTPUT" -> {
                    if (isBlank(cfg.path("parameterSetId").asText(""))) {
                        issues.add(ValidationIssue.error("INTEGRATION_NO_REF", node.id(),
                                node.nodeType() + " node '" + label(node) + "' has no parameter set selected."));
                    }
                }
                case "VECTOR_SEARCH" -> {
                    if (isBlank(cfg.path("knowledgeBaseId").asText(""))) {
                        issues.add(ValidationIssue.error("VECTOR_SEARCH_NO_KB", node.id(),
                                "Vector-search node '" + label(node) + "' has no knowledge base selected."));
                    }
                    if (isBlank(cfg.path("queryVariable").asText(""))) {
                        issues.add(ValidationIssue.error("VECTOR_SEARCH_NO_QUERY", node.id(),
                                "Vector-search node '" + label(node) + "' has no query input."));
                    }
                }
                case "NOTIFY" -> {
                    if ("USER".equals(cfg.path("recipientType").asText("USER"))
                            && isBlank(cfg.path("recipientUserId").asText(""))) {
                        issues.add(ValidationIssue.error("NOTIFY_NO_RECIPIENT", node.id(),
                                "Notify node '" + label(node) + "' has no recipient selected."));
                    }
                    // Content is satisfied by a template OR an inline title/message (mirrors SEND_EMAIL).
                    boolean noContent = isBlank(cfg.path("templateId").asText(""))
                            && isBlank(cfg.path("message").asText("")) && isBlank(cfg.path("title").asText(""));
                    if (noContent) {
                        issues.add(ValidationIssue.error("NOTIFY_NO_MESSAGE", node.id(),
                                "Notify node '" + label(node) + "' has no message."));
                    }
                }
                case "FOREACH" -> {
                    if (isBlank(cfg.path("sourceVariable").asText(""))) {
                        issues.add(ValidationIssue.error("FOREACH_NO_SOURCE", node.id(),
                                "For-each node '" + label(node) + "' has no source list variable selected."));
                    }
                }
                default -> { /* no required-config rule */ }
            }

            // ── DANGLING_VARIABLE ─────────────────────────────────────────
            checkDangling(node, cfg, nodes, edges, constants, issues);
        }

        return AutomationValidationResult.of(issues);
    }

    private void validateEmailAction(NodeView node, JsonNode cfg, List<ValidationIssue> issues) {
        String mode = cfg.path("actionMode").asText("REPLY");
        boolean bad = switch (mode) {
            case "FORWARD" -> isBlank(cfg.path("toAddress").asText(""));
            case "MOVE_FOLDER" -> isBlank(cfg.path("folder").asText(""));
            default -> { // REPLY
                String source = cfg.path("contentSource").asText("VORLAGE");
                yield "MANUAL".equals(source)
                        ? isBlank(cfg.path("body").asText(""))
                        : isBlank(cfg.path("templateId").asText(""));
            }
        };
        if (bad) {
            issues.add(ValidationIssue.error("EMAILACTION_NO_TARGET", node.id(),
                    "Email-action node '" + label(node) + "' is missing its target (recipient, folder, or template/body)."));
        }
    }

    // ── Dangling variable detection (namespace-level, mirrors VariableGraphService) ──

    private void checkDangling(NodeView node, JsonNode cfg, List<NodeView> nodes, List<EdgeView> edges,
                               Set<String> constants, List<ValidationIssue> issues) {
        Set<String> available = availableNamespaces(node.id(), nodes, edges);
        Set<String> referenced = new LinkedHashSet<>();

        switch (node.nodeType()) {
            case "SEND_EMAIL" -> {
                addTokens(cfg.path("subject").asText(""), referenced);
                addTokens(cfg.path("body").asText(""), referenced);
            }
            case "EMAIL_ACTION" -> {
                addTokens(cfg.path("subject").asText(""), referenced);
                addTokens(cfg.path("body").asText(""), referenced);
            }
            case "WEBHOOK" -> {
                addTokens(cfg.path("url").asText(""), referenced);
                addTokens(cfg.path("body").asText(""), referenced);
                JsonNode headers = cfg.get("headers");
                if (headers != null && headers.isArray()) {
                    for (JsonNode h : headers) addTokens(h.path("value").asText(""), referenced);
                }
            }
            case "OUTPUT" -> addMappingTokens(cfg.get("outputMappings"), referenced);
            case "INTEGRATION_CALL" -> addMappingTokens(cfg.get("inputMappings"), referenced);
            case "VECTOR_SEARCH" -> addTokens(cfg.path("queryVariable").asText(""), referenced);
            case "NOTIFY" -> {
                addTokens(cfg.path("title").asText(""), referenced);
                addTokens(cfg.path("message").asText(""), referenced);
            }
            case "FILTER" -> collectFilterFields(cfg, referenced);
            case "EXTRACT", "CATEGORIZE" -> {
                JsonNode sv = cfg.get("sourceVariables");
                if (sv != null && sv.isArray()) {
                    for (JsonNode v : sv) addReference(v.asText(""), referenced);
                }
            }
            case "FOREACH" -> addReference(cfg.path("sourceVariable").asText(""), referenced);
            default -> { /* node has no variable-bearing fields */ }
        }

        for (String ns : referenced) {
            if (ALWAYS_ALLOWED_NS.contains(ns) || available.contains(ns)) continue;
            issues.add(ValidationIssue.warning("DANGLING_VARIABLE", node.id(),
                    "Node '" + label(node) + "' references '" + ns + ".*' but no upstream node produces it."));
        }
    }

    /** Namespaces produced by all nodes upstream of {@code nodeId}. */
    private Set<String> availableNamespaces(String nodeId, List<NodeView> nodes, List<EdgeView> edges) {
        Set<String> ns = new HashSet<>();
        Map<String, NodeView> byId = new HashMap<>();
        for (NodeView n : nodes) byId.put(n.id(), n);

        for (NodeView up : upstream(nodeId, edges, byId)) {
            JsonNode cfg = parse(up.config());
            switch (up.nodeType()) {
                case "TRIGGER" -> {
                    ns.add("trigger");
                    if ("EMAIL".equals(cfg.path("triggerMode").asText("EMAIL"))) ns.add("email");
                }
                case "EXTRACT" -> {
                    JsonNode ex = cfg.get("extractions");
                    int count = (ex != null && ex.isArray()) ? ex.size() : 0;
                    for (int i = 0; i < count; i++) ns.add("extraction_" + i);
                }
                case "CATEGORIZE" -> ns.add("category");
                case "WEBHOOK" -> ns.add("http_" + nsKey(up));
                case "INPUT" -> ns.add("input");
                case "INTEGRATION_CALL" -> ns.add("integration_" + nsKey(up));
                case "VECTOR_SEARCH" -> ns.add("vectorsearch_" + nsKey(up));
                case "NOTIFY" -> ns.add("notify_" + nsKey(up));
                case "FOREACH" -> {
                    String alias = cfg.path("itemAlias").asText("item");
                    ns.add(isBlank(alias) ? "item" : alias);
                }
                default -> { /* produces no variables */ }
            }
        }
        return ns;
    }

    /** Breadth-first walk back over edges collecting all nodes upstream of {@code nodeId}. */
    private List<NodeView> upstream(String nodeId, List<EdgeView> edges, Map<String, NodeView> byId) {
        List<NodeView> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(nodeId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) continue;
            for (EdgeView e : edges) {
                if (e.targetId().equals(current) && !visited.contains(e.sourceId())) {
                    NodeView src = byId.get(e.sourceId());
                    if (src != null) {
                        result.add(src);
                        queue.add(e.sourceId());
                    }
                }
            }
        }
        return result;
    }

    private void collectFilterFields(JsonNode cfg, Set<String> referenced) {
        JsonNode checks = cfg.get("checks");
        if (checks == null || !checks.isArray()) return;
        for (JsonNode check : checks) {
            for (JsonNode group : check.path("groups")) {
                for (JsonNode cond : group.path("conditions")) {
                    addReference(cond.path("field").asText(""), referenced);
                }
            }
        }
    }

    private void addMappingTokens(JsonNode mappings, Set<String> referenced) {
        if (mappings == null || !mappings.isObject()) return;
        mappings.forEach(v -> addTokens(v.asText(""), referenced));
    }

    private void addTokens(String text, Set<String> referenced) {
        if (text == null || text.isEmpty()) return;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            addReference(m.group(1), referenced);
        }
    }

    /** Reduces a raw variable reference (e.g. {@code extraction_0.amount}) to its namespace. */
    private void addReference(String raw, Set<String> referenced) {
        if (raw == null) return;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return;
        int dot = trimmed.indexOf('.');
        String namespace = dot >= 0 ? trimmed.substring(0, dot) : trimmed;
        if (!namespace.isEmpty()) referenced.add(namespace);
    }

    private JsonNode parse(String config) {
        try {
            return objectMapper.readTree(config == null || config.isBlank() ? "{}" : config);
        } catch (Exception e) {
            log.debug("Failed to parse node config for validation: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode arrayNode) {
        List<JsonNode> list = new ArrayList<>();
        arrayNode.forEach(list::add);
        return list.stream();
    }

    private static String label(NodeView node) {
        return node.label() != null && !node.label().isBlank() ? node.label() : node.nodeType();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
