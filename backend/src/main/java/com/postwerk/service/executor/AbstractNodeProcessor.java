package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Base class for {@link NodeProcessor} implementations that share the common
 * "parse the node config JSON, run, and halt with {@code ERROR} on failure" envelope.
 *
 * <p>Subclasses implement {@link #doProcess(JsonNode, AutomationNode, ExecutionContext, UUID)}
 * with the already-parsed config; any thrown exception is logged and converted into a
 * {@code halt(ERROR)} result — preserving the exact behavior previously duplicated across
 * the individual processors.</p>
 *
 * @since 1.0
 */
public abstract class AbstractNodeProcessor implements NodeProcessor {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper;

    protected AbstractNodeProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public final NodeProcessorResult process(AutomationNode node, ExecutionContext context, UUID userId) {
        try {
            JsonNode config = objectMapper.readTree(node.getConfig());
            return doProcess(config, node, context, userId);
        } catch (Exception e) {
            log.error("{} node {} failed: {}", getNodeType(), node.getId(), e.getMessage());
            return NodeProcessorResult.halt(NodeResultStatus.ERROR, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Performs the node-specific work with the already-parsed {@code config}.
     * Thrown exceptions are handled uniformly by {@link #process}.
     */
    protected abstract NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                                     ExecutionContext context, UUID userId) throws Exception;
}
