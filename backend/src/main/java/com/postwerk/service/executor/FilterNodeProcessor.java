package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Processes FILTER nodes by evaluating multi-check DNF conditions against
 * the accumulated execution context variables. Routes to "check_N" or "fallback"
 * handles based on which check matches first.
 *
 * @since 2.0
 */
@Component
public class FilterNodeProcessor implements NodeProcessor {

    private final VariableFilterEvaluator variableFilterEvaluator;

    public FilterNodeProcessor(VariableFilterEvaluator variableFilterEvaluator) {
        this.variableFilterEvaluator = variableFilterEvaluator;
    }

    @Override
    public NodeType getNodeType() { return NodeType.FILTER; }

    @Override
    public NodeProcessorResult process(AutomationNode node, ExecutionContext context, UUID userId) {
        var result = variableFilterEvaluator.evaluate(node.getConfig(), context.getVariables());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("matched", result.matched());
        detail.put("matchedCheck", result.matchedCheckIndex());
        detail.put("conditions", result.conditionResults());

        if (result.matched()) {
            String handle = "check_" + result.matchedCheckIndex();
            return NodeProcessorResult.byHandle(
                    NodeResultStatus.MATCHED, detail, Set.of(handle));
        } else {
            return NodeProcessorResult.byHandle(
                    NodeResultStatus.NOT_MATCHED, detail, Set.of("fallback"));
        }
    }
}
