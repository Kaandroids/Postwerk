package com.postwerk.service.executor;

import com.postwerk.dto.automation.NodeMock;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Processes WEBHOOK ("HTTP Request") action nodes by delegating HTTP execution to WebhookHttpExecutor.
 * Routes by the matched response branch ("resp_&lt;i&gt;" for the i-th responseSchema, or "unmatched"
 * when no branch's status condition matches) and injects http_&lt;nodeKey&gt;.* variables.
 *
 * @since 1.0
 */
@Component
public class WebhookNodeProcessor implements NodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebhookNodeProcessor.class);

    private final ObjectMapper objectMapper;
    private final WebhookHttpExecutor webhookHttpExecutor;
    private final WebhookResponseExtractor responseExtractor;

    public WebhookNodeProcessor(ObjectMapper objectMapper, WebhookHttpExecutor webhookHttpExecutor,
                                WebhookResponseExtractor responseExtractor) {
        this.objectMapper = objectMapper;
        this.webhookHttpExecutor = webhookHttpExecutor;
        this.responseExtractor = responseExtractor;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.WEBHOOK;
    }

    @Override
    public NodeProcessorResult process(AutomationNode node, ExecutionContext context, UUID userId) {
        try {
            NodeMock mock = context.getMock(node.getId());
            if (mock != null && mock.isMock()) {
                return processMock(node, context, mock);
            }

            JsonNode config = objectMapper.readTree(node.getConfig());
            boolean forceLive = mock != null && mock.isLive();
            WebhookHttpExecutor.WebhookCallResult result = webhookHttpExecutor.execute(config, context, userId, node.getId(), forceLive);

            if (result.simulated()) {
                return NodeProcessorResult.byHandle(NodeResultStatus.SIMULATED, result.detail(), Set.of(result.handle()));
            }

            // Store response as variables for downstream nodes
            if (result.parsedFields() != null) {
                String httpPrefix = "http_" + nsKey(node);
                context.addExtractedData(httpPrefix, result.parsedFields());
            }

            // Route by the response branch the executor matched (resp_<i> / unmatched).
            return NodeProcessorResult.byHandle(NodeResultStatus.EXECUTED, result.detail(), Set.of(result.handle()));

        } catch (Exception e) {
            log.error("Webhook node {} failed: {}", node.getId(), e.getMessage());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("error", "Webhook execution failed");
            detail.put("success", false);
            return NodeProcessorResult.byHandle(NodeResultStatus.ERROR, detail, Set.of("unmatched"));
        }
    }

    /**
     * Synthesizes a webhook result from a test-case mock instead of performing the real HTTP call.
     * Injects the mock's {@code response} map as {@code http_<nodeId>.*} variables and routes
     * the {@code success}/{@code failure} handle (failure when {@code forceError} is set).
     */
    private NodeProcessorResult processMock(AutomationNode node, ExecutionContext context, NodeMock mock) {
        Map<String, Object> response = mock.response() != null ? mock.response() : Map.of();
        if (!response.isEmpty()) {
            context.addExtractedData("http_" + nsKey(node), response);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mocked", true);
        if (mock.statusCode() != null) detail.put("statusCode", mock.statusCode());
        detail.put("fields", response.size());

        boolean failed = mock.shouldForceError();
        detail.put("success", !failed);
        // forceError → unmatched; otherwise route the mocked status through the matching branch.
        String handle;
        if (failed) {
            handle = "unmatched";
        } else {
            int statusCode = mock.statusCode() != null ? mock.statusCode() : 200;
            JsonNode responseSchemas = null;
            try {
                responseSchemas = objectMapper.readTree(node.getConfig()).get("responseSchemas");
            } catch (Exception ignored) { /* invalid config → unmatched */ }
            handle = responseExtractor.match(responseSchemas, statusCode).handle();
        }
        return NodeProcessorResult.byHandle(NodeResultStatus.SIMULATED, detail, Set.of(handle));
    }

    /** Node-scoped variable key — the friendly nodeKey, falling back to the raw id. */
    private static String nsKey(AutomationNode node) {
        return (node.getNodeKey() != null && !node.getNodeKey().isBlank())
                ? node.getNodeKey() : node.getId().toString();
    }
}
