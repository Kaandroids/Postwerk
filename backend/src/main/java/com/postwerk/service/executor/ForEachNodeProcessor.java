package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Processes FOREACH nodes — a generic iterator.
 *
 * <p>Reads a list-valued source variable (e.g. {@code email.attachments}) and fans out: the
 * downstream reachable from the {@code each} handle runs once per element, in order. Each iteration
 * gets a context where the current element is bound under an alias (default {@code item}): for a
 * map element, every field becomes {@code item.<field>}; for a scalar, the value becomes
 * {@code item}. Two helpers are always added — {@code item.index} (0-based) and {@code item.count}
 * (the source list size). All other variables (a, b, d …) flow through unchanged, so a list
 * {@code c} becomes {@code c[0]}, {@code c[1]} … across iterations.</p>
 *
 * <p>Bounded by {@link #MAX_ITERATIONS} as a runaway/cost safeguard (each iteration may invoke the
 * AI downstream); the overflow is dropped and noted in the trace detail.</p>
 *
 * @since 1.0
 */
@Component
public class ForEachNodeProcessor extends AbstractNodeProcessor {

    /** Safety cap on iterations per FOREACH run. */
    static final int MAX_ITERATIONS = 100;

    /** Default alias for the current element when none is configured. */
    static final String DEFAULT_ALIAS = "item";

    /** Output handle whose downstream forms the loop body. */
    static final String BODY_HANDLE = "each";

    public ForEachNodeProcessor(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.FOREACH;
    }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) {
        String sourceVariable = NodeConfigReader.text(config, "sourceVariable", null);
        if (sourceVariable == null || sourceVariable.isBlank()) {
            return NodeProcessorResult.halt(NodeResultStatus.ERROR,
                    Map.of("error", "FOREACH node has no source variable configured"));
        }

        String alias = NodeConfigReader.text(config, "itemAlias", DEFAULT_ALIAS);
        if (alias.isBlank()) {
            alias = DEFAULT_ALIAS;
        }

        List<?> items = asList(context.getVariable(sourceVariable));
        if (items.isEmpty()) {
            // Nothing to iterate — stop this branch (the body never runs).
            return NodeProcessorResult.halt(NodeResultStatus.SKIPPED,
                    Map.of("count", 0, "source", sourceVariable));
        }

        int total = items.size();
        int iterations = Math.min(total, MAX_ITERATIONS);

        boolean attachmentSource = AiAttachmentSupport.SOURCE_KEY.equals(sourceVariable);

        List<ExecutionContext> contexts = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            Map<String, Object> vars = new LinkedHashMap<>();
            bindElement(vars, alias, items.get(i));
            vars.put(alias + ".index", i);
            vars.put(alias + ".count", total);
            // When iterating the email's attachments, tag the element with its attachment index so a
            // downstream AI/forward node can fetch just this one's bytes (see AiAttachmentSupport).
            if (attachmentSource) {
                vars.put(alias + AiAttachmentSupport.ITEM_INDEX_SUFFIX, i);
            }
            contexts.add(context.withVariables(vars));
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("count", iterations);
        detail.put("source", sourceVariable);
        detail.put("alias", alias);
        if (total > iterations) {
            detail.put("truncatedFrom", total);
        }

        return NodeProcessorResult.fanOut(NodeResultStatus.PASSED, detail, BODY_HANDLE, contexts);
    }

    /** Binds a map element as {@code alias.<field>} entries, or a scalar element as {@code alias}. */
    private void bindElement(Map<String, Object> vars, String alias, Object element) {
        if (element instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                vars.put(alias + "." + entry.getKey(), entry.getValue());
            }
        } else {
            vars.put(alias, element);
        }
    }

    /** Returns the value as a list, or an empty list when it is null or not a list. */
    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}
