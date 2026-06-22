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
 * Processes EXTRACT nodes by delegating to {@link ExtractNodeExecutor} for
 * AI-powered structured data extraction. Stores results as variables
 * (extraction_N.fieldName) in the execution context for downstream use.
 *
 * @since 1.0
 */
@Component
public class ExtractNodeProcessor extends AbstractNodeProcessor {

    private final ExtractNodeExecutor extractNodeExecutor;

    public ExtractNodeProcessor(ExtractNodeExecutor extractNodeExecutor, ObjectMapper objectMapper) {
        super(objectMapper);
        this.extractNodeExecutor = extractNodeExecutor;
    }

    @Override
    public NodeType getNodeType() { return NodeType.EXTRACT; }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) {
        NodeMock mock = context.getMock(node.getId());
        if (mock != null && mock.isMock()) {
            return processMock(node, context, mock);
        }

        Map<String, Map<String, Object>> results = extractNodeExecutor.execute(context.getEmail(), config, userId, context);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("extractedValues", results);

        Set<String> activeHandles = new HashSet<>();
        Map<String, ExecutionContext> ctxMap = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : results.entrySet()) {
            if (!entry.getValue().containsKey("_error")) {
                String extractionKey = entry.getKey(); // e.g. "extraction_0"
                activeHandles.add(extractionKey);

                // Store each extracted field as a variable: extraction_0.fieldName
                Map<String, Object> vars = new HashMap<>();
                for (Map.Entry<String, Object> field : entry.getValue().entrySet()) {
                    vars.put(extractionKey + "." + field.getKey(), field.getValue());
                }
                ctxMap.put(extractionKey, context.withVariables(vars));
            }
        }

        return NodeProcessorResult.byHandleWithContext(NodeResultStatus.EXTRACTED, detail, activeHandles, ctxMap);
    }

    /**
     * Synthesizes extraction results from a test-case mock, skipping the real AI extraction call.
     * The mock's {@code response} maps each extraction handle (e.g. {@code extraction_0}) to a
     * field→value map, exposed downstream as {@code extraction_N.fieldName} variables. When
     * {@code forceError} is set no handle is activated, so the extract branch stops.
     */
    @SuppressWarnings("unchecked")
    private NodeProcessorResult processMock(AutomationNode node, ExecutionContext context, NodeMock mock) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mocked", true);

        if (mock.shouldForceError()) {
            detail.put("error", "Mocked extraction failure");
            return NodeProcessorResult.byHandleWithContext(
                    NodeResultStatus.EXTRACTED, detail, new HashSet<>(), new HashMap<>());
        }

        Map<String, Object> response = mock.response() != null ? mock.response() : Map.of();
        Set<String> activeHandles = new HashSet<>();
        Map<String, ExecutionContext> ctxMap = new HashMap<>();
        Map<String, Object> extractedView = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : response.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> group)) continue;
            String extractionKey = entry.getKey();
            activeHandles.add(extractionKey);

            Map<String, Object> vars = new HashMap<>();
            Map<String, Object> groupView = new LinkedHashMap<>();
            for (Map.Entry<String, Object> field : ((Map<String, Object>) group).entrySet()) {
                vars.put(extractionKey + "." + field.getKey(), field.getValue());
                groupView.put(field.getKey(), field.getValue());
            }
            extractedView.put(extractionKey, groupView);
            ctxMap.put(extractionKey, context.withVariables(vars));
        }

        detail.put("extractedValues", extractedView);
        return NodeProcessorResult.byHandleWithContext(NodeResultStatus.EXTRACTED, detail, activeHandles, ctxMap);
    }
}
