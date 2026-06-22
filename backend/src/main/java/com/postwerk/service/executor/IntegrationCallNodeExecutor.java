package com.postwerk.service.executor;

import com.postwerk.dto.automation.NodeMock;
import com.postwerk.model.Automation;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.AutomationKind;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.service.AutomationExecutorService;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Processes the {@code INTEGRATION_CALL} node — invokes a reusable {@code INTEGRATION}-kind automation
 * from inside a normal automation, passing per-call parameters via a two-zone mapper.
 *
 * <p>Config shape:</p>
 * <pre>{@code
 * {
 *   "integrationId": "<uuid>",
 *   "inputMappings":    { "<inputField>": "<expression with {{...}}>" },
 *   "instanceSettings": { "<CONST_NAME>": "<value>" }   // Phase 1: stored only, not injected
 * }
 * }</pre>
 *
 * <p>Resolves each input expression against the current context, runs the integration sub-flow, and
 * exposes its output under {@code integration_<callNodeId>.*}. Routes the static {@code done} handle on
 * success and {@code failure} on error (the integration returns 0 or 1 result, hence no dynamic handles).</p>
 *
 * @since 1.0
 */
@Component
public class IntegrationCallNodeExecutor implements NodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(IntegrationCallNodeExecutor.class);

    private final ObjectMapper objectMapper;
    private final VariableResolver variableResolver;
    private final AutomationRepository automationRepository;
    private final AutomationExecutorService executorService;

    public IntegrationCallNodeExecutor(ObjectMapper objectMapper,
                                       VariableResolver variableResolver,
                                       AutomationRepository automationRepository,
                                       @Lazy AutomationExecutorService executorService) {
        this.objectMapper = objectMapper;
        this.variableResolver = variableResolver;
        this.automationRepository = automationRepository;
        this.executorService = executorService;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.INTEGRATION_CALL;
    }

    @Override
    public boolean requiresEmailContext() {
        return false;
    }

    @Override
    public NodeProcessorResult process(AutomationNode node, ExecutionContext context, UUID userId) {
        try {
            NodeMock mock = context.getMock(node.getId());
            if (mock != null && mock.isMock()) {
                return processMock(node, context, mock);
            }

            JsonNode config = objectMapper.readTree(node.getConfig());

            String integrationId = NodeConfigReader.text(config, "integrationId", null);
            if (integrationId == null || integrationId.isBlank()) {
                return failure("No integration selected");
            }

            UUID orgId = context.getOrganizationId();
            Automation integration = (orgId != null
                    ? automationRepository.findByIdAndOrganizationId(UUID.fromString(integrationId), orgId)
                    : automationRepository.findByIdAndUserId(UUID.fromString(integrationId), userId))
                    .orElse(null);
            if (integration == null) {
                return failure("Integration not found: " + integrationId);
            }
            if (integration.getKind() != AutomationKind.INTEGRATION) {
                return failure("Referenced automation is not an integration");
            }

            // Resolve the dynamic input expressions against the current context.
            Map<String, Object> inputFields = new LinkedHashMap<>();
            JsonNode inputMappings = config.get("inputMappings");
            if (inputMappings != null && inputMappings.isObject()) {
                inputMappings.fieldNames().forEachRemaining(field ->
                        inputFields.put(field, variableResolver.resolve(inputMappings.get(field).asText(""), context)));
            }

            int depth = context.getIntegrationDepth();
            Map<String, Object> output = executorService.runIntegration(integration, context, inputFields, depth + 1);

            // Expose the integration's output under integration_<callNodeId>.*
            String prefix = "integration_" + (node.getNodeKey() != null && !node.getNodeKey().isBlank()
                    ? node.getNodeKey() : node.getId().toString()) + ".";
            Map<String, Object> outVars = new LinkedHashMap<>();
            output.forEach((k, v) -> outVars.put(prefix + k, v));
            ExecutionContext enriched = context.withVariables(outVars);

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("integration", integration.getName());
            detail.put("inputFields", inputFields.size());
            detail.put("output", output);

            return NodeProcessorResult.byHandleWithContext(
                    NodeResultStatus.EXECUTED, detail, Set.of("done"), Map.of("done", enriched));

        } catch (Exception e) {
            log.error("Integration call node {} failed: {}", node.getId(), e.getMessage());
            return failure(e.getMessage());
        }
    }

    /**
     * Synthesizes the integration's output from a test-case mock instead of running the sub-flow.
     * The mock's {@code response} map is exposed under {@code integration_<callNodeId>.*} and the
     * {@code done} handle is routed (or {@code failure} when {@code forceError} is set).
     */
    private NodeProcessorResult processMock(AutomationNode node, ExecutionContext context, NodeMock mock) {
        if (mock.shouldForceError()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("mocked", true);
            detail.put("error", "Mocked integration failure");
            return NodeProcessorResult.byHandle(NodeResultStatus.ERROR, detail, Set.of("failure"));
        }

        Map<String, Object> output = mock.response() != null ? mock.response() : Map.of();

        String prefix = "integration_" + (node.getNodeKey() != null && !node.getNodeKey().isBlank()
                ? node.getNodeKey() : node.getId().toString()) + ".";
        Map<String, Object> outVars = new LinkedHashMap<>();
        output.forEach((k, v) -> outVars.put(prefix + k, v));
        ExecutionContext enriched = context.withVariables(outVars);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mocked", true);
        detail.put("output", output);

        return NodeProcessorResult.byHandleWithContext(
                NodeResultStatus.EXECUTED, detail, Set.of("done"), Map.of("done", enriched));
    }

    private NodeProcessorResult failure(String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("error", message);
        return NodeProcessorResult.byHandle(NodeResultStatus.ERROR, detail, Set.of("failure"));
    }
}
