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
 * Processes REMOVE_LABEL nodes by removing specified category assignments from the email.
 * Delegates to {@link RemoveLabelNodeExecutor} for persistence.
 *
 * @since 1.0
 */
@Component
public class RemoveLabelNodeProcessor extends AbstractNodeProcessor {

    private final RemoveLabelNodeExecutor removeLabelNodeExecutor;

    public RemoveLabelNodeProcessor(RemoveLabelNodeExecutor removeLabelNodeExecutor, ObjectMapper objectMapper) {
        super(objectMapper);
        this.removeLabelNodeExecutor = removeLabelNodeExecutor;
    }

    @Override
    public NodeType getNodeType() { return NodeType.REMOVE_LABEL; }

    @Override
    public boolean requiresEmailContext() { return true; }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) {
        Map<String, Object> detail = removeLabelNodeExecutor.execute(
                context.getEmail(), config, context.isDryRun());

        NodeResultStatus status = context.isDryRun()
                ? NodeResultStatus.SIMULATED : NodeResultStatus.EXECUTED;
        return NodeProcessorResult.followAll(status, detail);
    }
}
