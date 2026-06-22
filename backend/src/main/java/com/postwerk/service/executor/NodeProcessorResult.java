package com.postwerk.service.executor;

import com.postwerk.model.enums.NodeResultStatus;

import java.util.Map;
import java.util.Set;

/**
 * Result of a node processor execution, controlling downstream edge traversal.
 * Supports follow-all, halt, and selective handle-based routing strategies.
 *
 * @since 1.0
 */
public record NodeProcessorResult(
        NodeResultStatus status,
        Map<String, Object> detail,
        boolean followAllEdges,
        boolean haltTraversal,
        Set<String> activeHandles,
        Map<String, ExecutionContext> contextByHandle
) {
    public static NodeProcessorResult followAll(NodeResultStatus status, Map<String, Object> detail) {
        return new NodeProcessorResult(status, detail, true, false, Set.of(), Map.of());
    }

    public static NodeProcessorResult halt(NodeResultStatus status, Map<String, Object> detail) {
        return new NodeProcessorResult(status, detail, false, true, Set.of(), Map.of());
    }

    public static NodeProcessorResult byHandle(NodeResultStatus status, Map<String, Object> detail,
                                                 Set<String> handles) {
        return new NodeProcessorResult(status, detail, false, false, handles, Map.of());
    }

    public static NodeProcessorResult byHandleWithContext(NodeResultStatus status, Map<String, Object> detail,
                                                            Set<String> handles, Map<String, ExecutionContext> ctxMap) {
        return new NodeProcessorResult(status, detail, false, false, handles, ctxMap);
    }
}
