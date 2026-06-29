package com.postwerk.service.executor;

import com.postwerk.model.enums.NodeResultStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Result of a node processor execution, controlling downstream edge traversal.
 * Supports follow-all, halt, selective handle-based routing, and list fan-out.
 *
 * @since 1.0
 */
public record NodeProcessorResult(
        NodeResultStatus status,
        Map<String, Object> detail,
        boolean followAllEdges,
        boolean haltTraversal,
        Set<String> activeHandles,
        Map<String, ExecutionContext> contextByHandle,
        String fanOutHandle,
        List<ExecutionContext> fanOutContexts
) {
    public static NodeProcessorResult followAll(NodeResultStatus status, Map<String, Object> detail) {
        return new NodeProcessorResult(status, detail, true, false, Set.of(), Map.of(), null, List.of());
    }

    public static NodeProcessorResult halt(NodeResultStatus status, Map<String, Object> detail) {
        return new NodeProcessorResult(status, detail, false, true, Set.of(), Map.of(), null, List.of());
    }

    public static NodeProcessorResult byHandle(NodeResultStatus status, Map<String, Object> detail,
                                                 Set<String> handles) {
        return new NodeProcessorResult(status, detail, false, false, handles, Map.of(), null, List.of());
    }

    public static NodeProcessorResult byHandleWithContext(NodeResultStatus status, Map<String, Object> detail,
                                                            Set<String> handles, Map<String, ExecutionContext> ctxMap) {
        return new NodeProcessorResult(status, detail, false, false, handles, ctxMap, null, List.of());
    }

    /**
     * Fan-out: the downstream reachable from {@code handle} is traversed once per context in
     * {@code contexts}, in order — the basis for the FOREACH iterator node.
     */
    public static NodeProcessorResult fanOut(NodeResultStatus status, Map<String, Object> detail,
                                             String handle, List<ExecutionContext> contexts) {
        return new NodeProcessorResult(status, detail, false, false, Set.of(), Map.of(), handle, contexts);
    }
}
