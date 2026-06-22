package com.postwerk.service.executor;

import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates multi-check filter conditions against a variable map using
 * Disjunctive Normal Form (DNF) logic.
 *
 * <p>Each check contains groups (OR-combined), each group contains conditions (AND-combined).
 * Checks are evaluated top-to-bottom; the first matching check wins.
 * Returns the index of the matched check or -1 if none matched (fallback).</p>
 *
 * @since 2.0
 */
@Component
public class VariableFilterEvaluator {

    private static final Logger log = LoggerFactory.getLogger(VariableFilterEvaluator.class);
    private final ObjectMapper objectMapper;

    public VariableFilterEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates filter checks against the provided variables.
     *
     * @param configJson the node config JSON containing a "checks" array
     * @param variables  the accumulated variables from upstream nodes
     * @return result with matched check index and detailed condition evaluations
     */
    public VariableFilterResult evaluate(String configJson, Map<String, Object> variables) {
        List<Map<String, Object>> allConditionResults = new ArrayList<>();
        try {
            JsonNode config = objectMapper.readTree(configJson);
            JsonNode checksNode = config.get("checks");

            if (checksNode == null || !checksNode.isArray() || checksNode.isEmpty()) {
                return new VariableFilterResult(-1, List.of());
            }

            for (int ci = 0; ci < checksNode.size(); ci++) {
                JsonNode check = checksNode.get(ci);
                JsonNode groups = check.get("groups");
                String label = NodeConfigReader.text(check, "label", "Check " + ci);

                if (groups == null || !groups.isArray() || groups.isEmpty()) {
                    continue;
                }

                boolean checkMatched = false;
                for (int gi = 0; gi < groups.size(); gi++) {
                    JsonNode group = groups.get(gi);
                    JsonNode conditions = group.get("conditions");
                    if (conditions == null || !conditions.isArray()) continue;

                    boolean groupMatch = true;
                    for (JsonNode condition : conditions) {
                        String field = NodeConfigReader.text(condition, "field");
                        String operator = NodeConfigReader.text(condition, "operator");
                        String value = NodeConfigReader.text(condition, "value");

                        Object rawActual = variables.get(field);
                        String actualValue = rawActual != null ? String.valueOf(rawActual) : "";
                        boolean result = evaluateCondition(actualValue, operator, value);

                        Map<String, Object> cr = new LinkedHashMap<>();
                        cr.put("check", ci);
                        cr.put("checkLabel", label);
                        cr.put("group", gi);
                        cr.put("field", field);
                        cr.put("operator", operator);
                        cr.put("value", value);
                        cr.put("actual", actualValue);
                        cr.put("result", result);
                        allConditionResults.add(cr);

                        if (!result) groupMatch = false;
                    }
                    if (groupMatch) {
                        checkMatched = true;
                        break; // Short-circuit: first matching group in check
                    }
                }

                if (checkMatched) {
                    return new VariableFilterResult(ci, allConditionResults);
                }
            }

            return new VariableFilterResult(-1, allConditionResults);
        } catch (Exception e) {
            log.warn("Failed to evaluate filter config: {}", e.getMessage());
            return new VariableFilterResult(-1, allConditionResults);
        }
    }

    private boolean evaluateCondition(String fieldValue, String operator, String value) {
        return switch (operator) {
            case "EQUALS" -> fieldValue.equalsIgnoreCase(value);
            case "NOT_EQUALS" -> !fieldValue.equalsIgnoreCase(value);
            case "CONTAINS" -> fieldValue.toLowerCase().contains(value.toLowerCase());
            case "NOT_CONTAINS" -> !fieldValue.toLowerCase().contains(value.toLowerCase());
            case "STARTS_WITH" -> fieldValue.toLowerCase().startsWith(value.toLowerCase());
            case "ENDS_WITH" -> fieldValue.toLowerCase().endsWith(value.toLowerCase());
            case "IS_TRUE" -> "true".equalsIgnoreCase(fieldValue);
            case "IS_FALSE" -> "false".equalsIgnoreCase(fieldValue);
            case "GREATER_THAN" -> compareNumeric(fieldValue, value) > 0;
            case "LESS_THAN" -> compareNumeric(fieldValue, value) < 0;
            default -> {
                log.debug("Unknown operator: {}", operator);
                yield false;
            }
        };
    }

    private int compareNumeric(String a, String b) {
        try {
            double da = Double.parseDouble(a);
            double db = Double.parseDouble(b);
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }

    /**
     * Result of a variable-based filter evaluation.
     *
     * @param matchedCheckIndex index of the first matched check (-1 if none)
     * @param conditionResults  detailed per-condition evaluation results for tracing
     */
    public record VariableFilterResult(
            int matchedCheckIndex,
            List<Map<String, Object>> conditionResults
    ) {
        public boolean matched() {
            return matchedCheckIndex >= 0;
        }
    }
}
