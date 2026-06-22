package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeType;

import java.util.UUID;

/**
 * Strategy interface for automation node processors in the graph-based execution engine.
 * Each implementation handles a specific {@link NodeType} and returns a result
 * that controls edge traversal behavior.
 *
 * @since 1.0
 */
public interface NodeProcessor {
    NodeType getNodeType();
    NodeProcessorResult process(AutomationNode node, ExecutionContext context, UUID userId);

    /** Whether this node needs a real email + account context to run. */
    default boolean requiresEmailContext() { return false; }

    /**
     * Config-aware variant of {@link #requiresEmailContext()}. Lets a processor decide
     * email requirement based on the node's configuration (e.g. CATEGORIZE only needs an
     * email when its source variables reference {@code email.*}). Defaults to the
     * config-agnostic value.
     */
    default boolean requiresEmailContext(AutomationNode node) { return requiresEmailContext(); }
}
