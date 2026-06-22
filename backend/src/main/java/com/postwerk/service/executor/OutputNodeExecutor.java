package com.postwerk.service.executor;

import com.postwerk.dto.ParameterItemDto;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.ParameterSet;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.ParameterSetRepository;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Processes the {@code OUTPUT} node — the optional (0 or 1) return point of an {@code INTEGRATION}-kind
 * automation. Reads its {@code parameterSetId} (which declares the returned field shape) and resolves
 * each field's value from the node's {@code outputMappings} ({@code field → expression}, where the
 * expression may contain {@code {{...}}} placeholders) against the current execution variables. The
 * resolved values are written into the context's {@link ExecutionContext.OutputSink} and traversal
 * halts (the integration has returned).
 *
 * @since 1.0
 */
@Component
public class OutputNodeExecutor extends AbstractNodeProcessor {

    private final ParameterSetRepository parameterSetRepository;
    private final VariableResolver variableResolver;

    public OutputNodeExecutor(ObjectMapper objectMapper,
                              ParameterSetRepository parameterSetRepository,
                              VariableResolver variableResolver) {
        super(objectMapper);
        this.parameterSetRepository = parameterSetRepository;
        this.variableResolver = variableResolver;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.OUTPUT;
    }

    @Override
    public boolean requiresEmailContext() {
        return false;
    }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) throws Exception {
        Map<String, Object> resolved = new LinkedHashMap<>();
        JsonNode mappings = config.get("outputMappings");

        String parameterSetId = NodeConfigReader.text(config, "parameterSetId", null);
        if (parameterSetId != null && !parameterSetId.isBlank()) {
            ParameterSet parameterSet = parameterSetRepository.findById(UUID.fromString(parameterSetId)).orElse(null);
            if (parameterSet != null) {
                List<ParameterItemDto> params = objectMapper.readValue(
                        parameterSet.getParameters(), new TypeReference<>() {});
                for (ParameterItemDto p : params) {
                    String expr = mappings != null && mappings.has(p.name()) ? mappings.get(p.name()).asText("") : "";
                    resolved.put(p.name(), variableResolver.resolve(expr, context));
                }
            }
        }

        // Fall back to any extra mappings not covered by the parameter set.
        if (mappings != null && mappings.isObject()) {
            mappings.fieldNames().forEachRemaining(field -> {
                if (!resolved.containsKey(field)) {
                    resolved.put(field, variableResolver.resolve(mappings.get(field).asText(""), context));
                }
            });
        }

        if (context.getOutputSink() != null) {
            context.getOutputSink().capture(resolved);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("returned", resolved);
        return NodeProcessorResult.halt(NodeResultStatus.PASSED, detail);
    }
}
