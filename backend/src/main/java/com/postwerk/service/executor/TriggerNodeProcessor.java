package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.Email;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.util.SafeStrings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Processes TRIGGER nodes — the unified entry point of an automation workflow.
 * Supports four modes:
 * - EMAIL: monitors email accounts, injects email.* variables
 * - WEBHOOK: inbound receiver — the automation is triggered by an external HTTP POST;
 *   {@code trigger.*} variables are injected upstream by the ingress service, so the node
 *   is a simple passthrough that routes the incoming context to "output"
 * - MANUAL: user-fired — the run endpoint seeds {@code trigger.*} from user-entered parameter-set
 *   values; same passthrough as WEBHOOK
 * - CRON: time-based passthrough, routes to "output"
 *
 * @since 1.0
 */
@Component
public class TriggerNodeProcessor implements NodeProcessor {

    private static final Logger log = LoggerFactory.getLogger(TriggerNodeProcessor.class);

    private final ObjectMapper objectMapper;

    public TriggerNodeProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public NodeType getNodeType() { return NodeType.TRIGGER; }

    @Override
    public NodeProcessorResult process(AutomationNode node, ExecutionContext context, UUID userId) {
        String mode = readTriggerMode(node.getConfig());
        return switch (mode) {
            case "CRON" -> processCron();
            // MANUAL and WEBHOOK are both passthroughs that route the pre-seeded trigger.* context to
            // "output": for WEBHOOK the ingress service seeds them from the HTTP body, for MANUAL the
            // run endpoint seeds them from the user-entered parameter-set values.
            case "WEBHOOK", "MANUAL" -> processWebhook(context);
            default -> processEmail(context);
        };
    }

    private String readTriggerMode(String config) {
        try {
            JsonNode json = objectMapper.readTree(config);
            if (json.has("triggerMode")) {
                return json.get("triggerMode").asText("EMAIL");
            }
        } catch (Exception e) {
            log.warn("Failed to read triggerMode from config: {}", e.getMessage());
        }
        return "EMAIL";
    }

    private NodeProcessorResult processEmail(ExecutionContext context) {
        Email email = context.getEmail();
        boolean isReply = email.getInReplyTo() != null && !email.getInReplyTo().isBlank();

        Map<String, Object> emailVars = new LinkedHashMap<>();
        emailVars.put("email.from", SafeStrings.nullToEmpty(email.getFromAddress()));
        emailVars.put("email.fromName", SafeStrings.nullToEmpty(email.getFromPersonal()));
        emailVars.put("email.to", SafeStrings.nullToEmpty(email.getToAddresses()));
        emailVars.put("email.cc", SafeStrings.nullToEmpty(email.getCcAddresses()));
        emailVars.put("email.subject", SafeStrings.nullToEmpty(email.getSubject()));
        emailVars.put("email.body", SafeStrings.nullToEmpty(email.getBodyText()));
        emailVars.put("email.hasAttachments", email.isHasAttachments());
        emailVars.put("email.attachments", parseAttachments(email.getAttachments()));
        emailVars.put("email.isRead", email.isRead());
        emailVars.put("email.folder", SafeStrings.nullToEmpty(email.getFolder()));
        emailVars.put("email.receivedAt", email.getReceivedAt() != null ? email.getReceivedAt().toString() : "");
        emailVars.put("email.isReply", isReply);

        ExecutionContext enrichedContext = context.withVariables(emailVars);

        Map<String, Object> detail = Map.of("isReply", isReply);
        String handle = isReply ? "reply" : "new-email";

        return NodeProcessorResult.byHandleWithContext(
                NodeResultStatus.PASSED, detail,
                Set.of(handle),
                Map.of(handle, enrichedContext));
    }

    /**
     * Parses the email's attachment metadata JSON into a real list so it can be referenced as the
     * {@code email.attachments} variable (e.g. as a FOREACH source). Each element is a map with
     * {@code name}/{@code size}/{@code contentType}. Returns an empty list when absent or invalid.
     */
    private List<Map<String, Object>> parseAttachments(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse email attachments JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private NodeProcessorResult processCron() {
        return NodeProcessorResult.byHandle(NodeResultStatus.PASSED, Map.of(), Set.of("output"));
    }

    /**
     * Inbound webhook passthrough. The automation is triggered from the outside by an HTTP POST;
     * {@code trigger.*} variables are injected into the context by the ingress service at runtime,
     * or — in a dry-run test — seeded from the test case's {@code triggerPayload}. The node passes
     * whatever {@code trigger.*} variables are present through to the {@code output} handle so the
     * downstream graph can be exercised against the expected inbound data. When no trigger payload
     * is present in a dry-run (nothing to assert against), the node reports SIMULATED.
     */
    private NodeProcessorResult processWebhook(ExecutionContext context) {
        Map<String, Object> triggerVars = context.getVariablesByPrefix("trigger.");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("triggerFields", triggerVars.size());

        NodeResultStatus status = NodeResultStatus.PASSED;
        if (context.isDryRun()) {
            detail.put("simulated", true);
            detail.put("mode", "WEBHOOK");
            if (triggerVars.isEmpty()) {
                status = NodeResultStatus.SIMULATED;
            }
        }

        return NodeProcessorResult.byHandleWithContext(
                status, detail,
                Set.of("output"),
                Map.of("output", context));
    }
}
