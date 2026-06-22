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
 * Processes LABEL nodes by assigning one or more categories to the email.
 * Delegates to {@link LabelNodeExecutor} for persistence.
 *
 * @since 1.0
 */
@Component
public class LabelNodeProcessor extends AbstractNodeProcessor {

    private final LabelNodeExecutor labelNodeExecutor;

    public LabelNodeProcessor(LabelNodeExecutor labelNodeExecutor, ObjectMapper objectMapper) {
        super(objectMapper);
        this.labelNodeExecutor = labelNodeExecutor;
    }

    @Override
    public NodeType getNodeType() { return NodeType.LABEL; }

    @Override
    public boolean requiresEmailContext() { return true; }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) {
        Map<String, Object> detail = labelNodeExecutor.execute(
                context.getEmail(), config, context.isDryRun());

        NodeResultStatus status = context.isDryRun()
                ? NodeResultStatus.SIMULATED : NodeResultStatus.EXECUTED;
        return NodeProcessorResult.followAll(status, detail);
    }
}
