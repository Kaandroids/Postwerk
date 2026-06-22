package com.postwerk.service.executor;

import com.postwerk.dto.automation.NodeMock;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Processes CATEGORIZE nodes by delegating to {@link CategorizeNodeExecutor} for
 * AI-powered email classification. Always follows all output edges.
 *
 * @since 1.0
 */
@Component
public class CategorizeNodeProcessor extends AbstractNodeProcessor {

    private final CategorizeNodeExecutor categorizeNodeExecutor;

    public CategorizeNodeProcessor(CategorizeNodeExecutor categorizeNodeExecutor, ObjectMapper objectMapper) {
        super(objectMapper);
        this.categorizeNodeExecutor = categorizeNodeExecutor;
    }

    @Override
    public NodeType getNodeType() { return NodeType.CATEGORIZE; }

    @Override
    public boolean requiresEmailContext() { return true; }

    /**
     * Only requires an email when the classification source is the email itself. Categorizing
     * pure API/webhook text (source variables without {@code email.*}) runs without an email and
     * simply emits {@code category.*} downstream.
     */
    @Override
    public boolean requiresEmailContext(AutomationNode node) {
        try {
            return CategorizeNodeExecutor.sourcesIncludeEmail(objectMapper.readTree(node.getConfig()));
        } catch (Exception e) {
            return true; // malformed config — fail safe by requiring email
        }
    }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) {
        NodeMock mock = context.getMock(node.getId());
        if (mock != null && mock.isMock()) {
            return processMock(node, context, mock);
        }

        var catResult = categorizeNodeExecutor.executeDetailed(
                context.getEmail(), config, userId, context.isDryRun(), context);
        String outputHandle = catResult.outputHandle();

        Map<String, Object> detail = new LinkedHashMap<>();
        if (catResult.classificationResult() != null) {
            detail.put("categoryId", catResult.selectedCategoryId() != null
                    ? catResult.selectedCategoryId().toString() : null);
            detail.put("categoryName", catResult.selectedCategoryName());
            detail.put("category", catResult.selectedCategoryName());
            detail.put("categoryColor", catResult.selectedCategoryColor());
            detail.put("confidence", catResult.classificationResult().confidence());
            detail.put("reason", catResult.classificationResult().reason());
            detail.put("threshold", catResult.threshold());
            detail.put("accepted", catResult.accepted());
            detail.put("candidates", catResult.candidates().stream()
                    .map(c -> Map.of("id", c.id().toString(), "name", c.name()))
                    .toList());
        }

        NodeResultStatus status = catResult.accepted()
                ? NodeResultStatus.CATEGORIZED : NodeResultStatus.NOT_MATCHED;

        // Store category.* variables in context for downstream nodes
        Map<String, Object> categoryVars = new LinkedHashMap<>();
        if (catResult.selectedCategoryId() != null) {
            categoryVars.put("category.id", catResult.selectedCategoryId().toString());
        }
        categoryVars.put("category.name", catResult.selectedCategoryName() != null ? catResult.selectedCategoryName() : "");
        categoryVars.put("category.color", catResult.selectedCategoryColor() != null ? catResult.selectedCategoryColor() : "");
        if (catResult.classificationResult() != null) {
            categoryVars.put("category.confidence", catResult.classificationResult().confidence());
        }
        ExecutionContext enrichedContext = context.withVariables(categoryVars);
        // #3b: track the per-path minimum AI confidence so a downstream action's review gate
        // reflects the weakest classification on the path, not just the latest.
        if (catResult.classificationResult() != null) {
            enrichedContext = enrichedContext.withRecordedConfidence(catResult.classificationResult().confidence());
        }

        return NodeProcessorResult.byHandleWithContext(status, detail,
                Set.of(outputHandle), Map.of(outputHandle, enrichedContext));
    }

    /**
     * Synthesizes a classification result from a test-case mock, skipping the real embedding +
     * Gemini calls (which cost AI quota). The mock's {@code response} supplies the routed
     * {@code handle} (e.g. {@code category_0} / {@code uncategorized}) and the {@code category.*}
     * fields ({@code categoryId}, {@code categoryName}, {@code categoryColor}, {@code confidence}).
     */
    private NodeProcessorResult processMock(AutomationNode node, ExecutionContext context, NodeMock mock) {
        Map<String, Object> response = mock.response() != null ? mock.response() : Map.of();

        String handle = String.valueOf(response.getOrDefault("handle", "uncategorized"));
        boolean accepted = !mock.shouldForceError() && handle.startsWith("category_");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mocked", true);
        detail.put("categoryId", response.get("categoryId"));
        detail.put("categoryName", response.get("categoryName"));
        detail.put("category", response.get("categoryName"));
        detail.put("categoryColor", response.get("categoryColor"));
        detail.put("confidence", response.get("confidence"));
        detail.put("accepted", accepted);

        Map<String, Object> categoryVars = new LinkedHashMap<>();
        if (response.get("categoryId") != null) {
            categoryVars.put("category.id", String.valueOf(response.get("categoryId")));
        }
        categoryVars.put("category.name", response.getOrDefault("categoryName", ""));
        categoryVars.put("category.color", response.getOrDefault("categoryColor", ""));
        if (response.get("confidence") != null) {
            categoryVars.put("category.confidence", response.get("confidence"));
        }
        ExecutionContext enriched = context.withVariables(categoryVars);
        if (response.get("confidence") instanceof Number n) {
            enriched = enriched.withRecordedConfidence(n.doubleValue()); // #3b min-confidence tracking
        }

        NodeResultStatus status = accepted ? NodeResultStatus.CATEGORIZED : NodeResultStatus.NOT_MATCHED;
        return NodeProcessorResult.byHandleWithContext(status, detail,
                Set.of(handle), Map.of(handle, enriched));
    }
}
