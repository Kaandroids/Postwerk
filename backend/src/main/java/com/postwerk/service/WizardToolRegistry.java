package com.postwerk.service;

import com.postwerk.dto.WizardSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Virtual tool registry for the wizard. Tools don't write to DB —
 * they generate temporary UUIDs and store results in the Redis session.
 */
@Component
public class WizardToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(WizardToolRegistry.class);

    private final ObjectMapper objectMapper;
    private final AutomationValidator automationValidator;

    public WizardToolRegistry(ObjectMapper objectMapper, AutomationValidator automationValidator) {
        this.objectMapper = objectMapper;
        this.automationValidator = automationValidator;
    }

    /**
     * Returns tool declarations available during wizard chatting phase.
     */
    public Tool getChatToolDeclarations() {
        return Tool.builder().functionDeclarations(List.of(
                proposeAutomationPlan()
        )).build();
    }

    /**
     * Returns tool declarations available during wizard building phase.
     */
    public Tool getBuildToolDeclarations() {
        return Tool.builder().functionDeclarations(List.of(
                AiToolDeclarations.createCategory(),
                AiToolDeclarations.createParameterSet(),
                AiToolDeclarations.createTemplate(),
                AiToolDeclarations.createAutomation(),
                AiToolDeclarations.updateAutomationFlow()
        )).build();
    }

    /**
     * Executes a virtual tool, storing results in the session.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeTool(String name, Map<String, Object> args, WizardSession session) {
        try {
            final List<com.postwerk.dto.automation.ValidationIssue>[] flowIssues = new List[]{null};
            Object result = switch (name) {
                case "propose_automation_plan" -> {
                    String planSummary = (String) args.getOrDefault("planSummary", "");
                    session.setAutomationPlan(Map.of("planSummary", planSummary));
                    yield Map.of("proposed", true, "planSummary", planSummary,
                            "message", "Plan accepted. Now create all dependencies (categories, filters, parameter sets, templates) first, then create the automation and set its flow.");
                }
                case "create_category" -> {
                    UUID tempId = UUID.randomUUID();
                    Map<String, Object> data = new LinkedHashMap<>(args);
                    data.put("id", tempId.toString());
                    data.put("locked", false);
                    data.put("createdAt", java.time.Instant.now().toString());
                    storeToolResult(session, name, args, data);
                    yield data;
                }
                case "create_parameter_set" -> {
                    UUID tempId = UUID.randomUUID();
                    Map<String, Object> data = new LinkedHashMap<>(args);
                    data.put("id", tempId.toString());
                    data.put("locked", false);
                    data.put("createdAt", java.time.Instant.now().toString());
                    storeToolResult(session, name, args, data);
                    yield data;
                }
                case "create_template" -> {
                    UUID tempId = UUID.randomUUID();
                    Map<String, Object> data = new LinkedHashMap<>(args);
                    data.put("id", tempId.toString());
                    data.put("locked", false);
                    data.put("createdAt", java.time.Instant.now().toString());
                    storeToolResult(session, name, args, data);
                    yield data;
                }
                case "create_automation" -> {
                    UUID tempId = UUID.randomUUID();
                    Map<String, Object> data = new LinkedHashMap<>(args);
                    data.put("id", tempId.toString());
                    data.put("status", "PAUSED");
                    data.put("locked", false);
                    data.put("createdAt", java.time.Instant.now().toString());
                    storeToolResult(session, name, args, data);
                    yield data;
                }
                case "update_automation_flow" -> {
                    Map<String, Object> data = new LinkedHashMap<>(args);
                    storeToolResult(session, name, args, data);
                    // Store in automationPlan for the canvas visualization
                    Map<String, Object> plan = session.getAutomationPlan() != null
                            ? new LinkedHashMap<>(session.getAutomationPlan()) : new LinkedHashMap<>();
                    plan.put("nodes", args.get("nodes"));
                    plan.put("edges", args.get("edges"));
                    plan.put("automationId", args.get("automationId"));
                    session.setAutomationPlan(plan);
                    // Attach validation issues (best-effort, namespace-level) so the wizard AI can
                    // self-correct an incomplete flow before finishing — mirrors the dashboard assistant.
                    var validation = automationValidator.validate(
                            com.postwerk.model.enums.AutomationKind.AUTOMATION,
                            nodeViews(args.get("nodes")), edgeViews(args.get("edges")), Set.of());
                    flowIssues[0] = validation.issues();
                    yield data;
                }
                default -> throw new IllegalArgumentException("Unknown wizard tool: " + name);
            };
            if (flowIssues[0] != null && !flowIssues[0].isEmpty()) {
                return Map.of("success", true, "data", result, "validationIssues", flowIssues[0]);
            }
            return Map.of("success", true, "data", result);
        } catch (Exception e) {
            log.warn("Wizard tool execution failed: {} - {}", name, e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** Adapts the raw tool-arg {@code nodes} list into validator {@link AutomationValidator.NodeView}s. */
    @SuppressWarnings("unchecked")
    private List<AutomationValidator.NodeView> nodeViews(Object nodesArg) {
        if (!(nodesArg instanceof List<?> list)) return List.of();
        List<AutomationValidator.NodeView> views = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> node = (Map<String, Object>) m;
            // Wizard nodes are in-session (no persisted nodeKey yet) — use the node id as the
            // variable-namespace key so it matches the {{http_<id>.x}} tokens the AI writes.
            views.add(new AutomationValidator.NodeView(
                    str(node.get("id")), str(node.get("nodeType")), str(node.get("label")),
                    str(node.get("config")), str(node.get("id"))));
        }
        return views;
    }

    /** Adapts the raw tool-arg {@code edges} list into validator {@link AutomationValidator.EdgeView}s. */
    @SuppressWarnings("unchecked")
    private List<AutomationValidator.EdgeView> edgeViews(Object edgesArg) {
        if (!(edgesArg instanceof List<?> list)) return List.of();
        List<AutomationValidator.EdgeView> views = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> edge = (Map<String, Object>) m;
            views.add(new AutomationValidator.EdgeView(
                    str(edge.get("sourceNodeId")), str(edge.get("sourceHandle")),
                    str(edge.get("targetNodeId")), str(edge.get("targetHandle"))));
        }
        return views;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private void storeToolResult(WizardSession session, String toolName,
                                 Map<String, Object> args, Map<String, Object> result) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tool", toolName);
        entry.put("args", args);
        entry.put("result", result);
        session.getToolResults().add(entry);
    }

    // ─── Tool Declarations ──────────────────────────────────────────────

    private FunctionDeclaration proposeAutomationPlan() {
        return FunctionDeclaration.builder()
                .name("propose_automation_plan")
                .description("Call this once you have gathered enough information from the user. " +
                        "This signals the transition from chatting to building. After calling this, " +
                        "immediately start creating all dependencies and the automation.")
                .parameters(Schema.builder()
                        .type("OBJECT")
                        .properties(Map.of(
                                "planSummary", Schema.builder().type("STRING")
                                        .description("Summary of the automation plan: nodes, dependencies, flow logic.")
                                        .build()
                        ))
                        .required(List.of("planSummary"))
                        .build())
                .build();
    }

}
