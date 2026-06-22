package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Processes the {@code INPUT} node — the mandatory, single entry point of an {@code INTEGRATION}-kind
 * automation. The {@code input.*} variables are already seeded into the {@link ExecutionContext} by
 * the calling {@code IntegrationCallNodeExecutor} (via the sub-execution primitive) before traversal
 * begins, so this node is a simple pass-through that follows all outgoing edges.
 *
 * @since 1.0
 */
@Component
public class InputNodeExecutor implements NodeProcessor {

    @Override
    public NodeType getNodeType() {
        return NodeType.INPUT;
    }

    @Override
    public boolean requiresEmailContext() {
        return false;
    }

    @Override
    public NodeProcessorResult process(AutomationNode node, ExecutionContext context, UUID userId) {
        return NodeProcessorResult.followAll(NodeResultStatus.PASSED, Map.of("inputFields", context.getVariablesByPrefix("input.").size()));
    }
}
