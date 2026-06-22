package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Processes DELAY nodes by queueing the email for deferred execution after a configured duration.
 * Halts traversal until the delay period expires.
 *
 * @since 1.0
 */
@Component
public class DelayNodeProcessor extends AbstractNodeProcessor {

    private final DelayNodeExecutor delayNodeExecutor;

    public DelayNodeProcessor(DelayNodeExecutor delayNodeExecutor, ObjectMapper objectMapper) {
        super(objectMapper);
        this.delayNodeExecutor = delayNodeExecutor;
    }

    @Override
    public NodeType getNodeType() { return NodeType.DELAY; }

    @Override
    public boolean requiresEmailContext() { return true; }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) {
        UUID automationId = node.getAutomation() != null ? node.getAutomation().getId() : null;
        Map<String, Object> detail = delayNodeExecutor.execute(
                context.getEmail(), automationId, node.getId(), config, context.isDryRun());

        if (context.isDryRun()) {
            return NodeProcessorResult.followAll(NodeResultStatus.SIMULATED, detail);
        } else {
            return NodeProcessorResult.halt(NodeResultStatus.EXECUTED, detail);
        }
    }
}
