package com.postwerk.service.executor;

import com.postwerk.dto.KbSearchResult;
import com.postwerk.dto.automation.NodeMock;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.service.KnowledgeBaseSearchService;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Processes the {@code VECTOR_SEARCH} node — resolves a query expression, runs a hybrid
 * knowledge-base retrieval + LLM judge ({@link KnowledgeBaseSearchService}), and routes on a
 * confidence threshold.
 *
 * <p>Two output handles, three statuses (see design D9): a match at/above the threshold →
 * {@code MATCHED} on {@code success}; no match / below threshold → {@code NOT_MATCHED} on
 * {@code fail}; a failure (KB missing, Gemini error) → {@code ERROR} on {@code fail}. The chosen
 * entry's fields plus {@code confidence}/{@code reason} are injected under
 * {@code vectorsearch_<nodeId>.*}. Mockable in dry-run/tests like CATEGORIZE / INTEGRATION_CALL.</p>
 *
 * @since 1.0
 */
@Component
public class VectorSearchNodeProcessor extends AbstractNodeProcessor {

    private final VariableResolver variableResolver;
    private final KnowledgeBaseSearchService searchService;

    public VectorSearchNodeProcessor(ObjectMapper objectMapper,
                                     VariableResolver variableResolver,
                                     KnowledgeBaseSearchService searchService) {
        super(objectMapper);
        this.variableResolver = variableResolver;
        this.searchService = searchService;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.VECTOR_SEARCH;
    }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) {
        NodeMock mock = context.getMock(node.getId());
        if (mock != null && mock.isMock()) {
            return processMock(node, context, mock);
        }

        String prefix = varPrefix(node);
        String kbId = NodeConfigReader.text(config, "knowledgeBaseId", null);
        if (kbId == null || kbId.isBlank()) {
            return NodeProcessorResult.byHandle(NodeResultStatus.ERROR,
                    Map.of("error", "No knowledge base selected"), Set.of("fail"));
        }

        String queryTemplate = NodeConfigReader.text(config, "queryVariable", null);
        int topK = NodeConfigReader.integer(config, "topK", 5);
        int threshold = NodeConfigReader.integer(config, "confidenceThreshold", 90);
        String queryText = queryTemplate == null ? "" : variableResolver.resolve(queryTemplate, context);

        KbSearchResult result = searchService.search(
                context.getOrganizationId(), userId, UUID.fromString(kbId), queryText, topK, threshold);

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put(prefix + "confidence", result.confidence());
        vars.put(prefix + "reason", result.reason() == null ? "" : result.reason());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("query", queryText);
        detail.put("confidence", result.confidence());
        detail.put("reason", result.reason());
        detail.put("candidateCount", result.candidateCount());

        String handle;
        NodeResultStatus status;
        switch (result.status()) {
            case MATCHED -> {
                handle = "success";
                status = NodeResultStatus.MATCHED;
                result.match().forEach((k, v) -> vars.put(prefix + "match." + k, v));
                detail.put("match", result.match());
            }
            case ERROR -> {
                handle = "fail";
                status = NodeResultStatus.ERROR;
                detail.put("error", result.reason());
            }
            default -> {
                handle = "fail";
                status = NodeResultStatus.NOT_MATCHED;
            }
        }

        ExecutionContext enriched = context.withVariables(vars);
        return NodeProcessorResult.byHandleWithContext(status, detail, Set.of(handle), Map.of(handle, enriched));
    }

    /**
     * Synthesizes a search outcome from a test-case mock, skipping the embedding + Gemini judge
     * (which cost AI quota). {@code response} supplies {@code handle} (success/fail), the matched
     * {@code match} field map, {@code confidence}, and {@code reason}; {@code forceError} → ERROR/fail.
     */
    @SuppressWarnings("unchecked")
    private NodeProcessorResult processMock(AutomationNode node, ExecutionContext context, NodeMock mock) {
        String prefix = varPrefix(node);
        if (mock.shouldForceError()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("mocked", true);
            detail.put("error", "Mocked search failure");
            return NodeProcessorResult.byHandle(NodeResultStatus.ERROR, detail, Set.of("fail"));
        }

        Map<String, Object> response = mock.response() != null ? mock.response() : Map.of();
        String handle = String.valueOf(response.getOrDefault("handle", "success"));
        boolean matched = "success".equals(handle);
        Object confidence = response.getOrDefault("confidence", matched ? 100 : 0);
        Object reason = response.getOrDefault("reason", "");
        Map<String, Object> matchData = (response.get("match") instanceof Map<?, ?> m)
                ? (Map<String, Object>) m : Map.of();

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put(prefix + "confidence", confidence);
        vars.put(prefix + "reason", reason);
        if (matched) {
            matchData.forEach((k, v) -> vars.put(prefix + "match." + k, v));
        }
        ExecutionContext enriched = context.withVariables(vars);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mocked", true);
        detail.put("confidence", confidence);
        detail.put("reason", reason);
        detail.put("match", matchData);

        NodeResultStatus status = matched ? NodeResultStatus.MATCHED : NodeResultStatus.NOT_MATCHED;
        return NodeProcessorResult.byHandleWithContext(status, detail, Set.of(handle), Map.of(handle, enriched));
    }

    private static String varPrefix(AutomationNode node) {
        String key = (node.getNodeKey() != null && !node.getNodeKey().isBlank())
                ? node.getNodeKey() : node.getId().toString();
        return "vectorsearch_" + key + ".";
    }
}
