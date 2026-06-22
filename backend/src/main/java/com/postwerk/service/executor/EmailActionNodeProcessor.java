package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Unified processor for EMAIL_ACTION nodes (REPLY, FORWARD, MOVE_FOLDER modes).
 * Replaces the separate ReplyNodeProcessor, ForwardNodeProcessor, and MoveNodeProcessor.
 *
 * @since 1.0
 */
@Component
public class EmailActionNodeProcessor extends AbstractNodeProcessor {

    private final Map<String, ActionExecutor> executors;
    private final TemplateRepository templateRepository;
    private final VariableResolver variableResolver;

    public EmailActionNodeProcessor(List<ActionExecutor> executors,
                                    TemplateRepository templateRepository,
                                    ObjectMapper objectMapper,
                                    VariableResolver variableResolver) {
        super(objectMapper);
        Map<String, ActionExecutor> byType = new HashMap<>();
        for (ActionExecutor executor : executors) {
            byType.put(executor.getActionType(), executor);
        }
        this.executors = byType;
        this.templateRepository = templateRepository;
        this.variableResolver = variableResolver;
    }

    @Override
    public NodeType getNodeType() { return NodeType.EMAIL_ACTION; }

    @Override
    public boolean requiresEmailContext() { return true; }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) throws Exception {
        String actionMode = config.has("actionMode") ? config.get("actionMode").asText("REPLY") : "REPLY";
        return switch (actionMode) {
            case "FORWARD" -> processForward(config, context);
            case "MOVE_FOLDER" -> processMove(config, context);
            default -> processReply(config, context);
        };
    }

    private NodeProcessorResult processReply(JsonNode config, ExecutionContext context) throws Exception {
        if (context.isDryRun()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("actionMode", "REPLY");
            detail.put("reason", "dry-run");

            String contentSource = NodeConfigReader.text(config, "contentSource", null);
            String templateId = NodeConfigReader.text(config, "templateId", null);

            if (!"MANUAL".equalsIgnoreCase(contentSource) && templateId != null && !templateId.isBlank()) {
                detail.put("contentSource", "VORLAGE");
                try {
                    templateRepository.findById(UUID.fromString(templateId)).ifPresent(template -> {
                        detail.put("templateName", template.getName());
                        detail.put("renderedSubject", variableResolver.resolve(
                                template.getSubject(), context));
                        detail.put("renderedBody", variableResolver.resolve(
                                template.getBody(), context));
                    });
                } catch (Exception e) {
                    log.warn("Failed to render template preview: {}", e.getMessage());
                }
            } else {
                detail.put("contentSource", "MANUAL");
                detail.put("renderedSubject", variableResolver.resolve(
                        NodeConfigReader.text(config, "subject", ""), context));
                detail.put("renderedBody", variableResolver.resolve(
                        NodeConfigReader.text(config, "body", ""), context));
            }
            return NodeProcessorResult.followAll(NodeResultStatus.SIMULATED, detail);
        }

        ActionExecutor replyExecutor = executors.get("REPLY_TEMPLATE");
        if (replyExecutor != null) {
            replyExecutor.execute(context.getEmail(), context.getAccount(), config, context);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("actionMode", "REPLY");
        return NodeProcessorResult.followAll(NodeResultStatus.EXECUTED, detail);
    }

    private NodeProcessorResult processForward(JsonNode config, ExecutionContext context) throws Exception {
        String toAddress = NodeConfigReader.text(config, "toAddress");

        if (context.isDryRun()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("actionMode", "FORWARD");
            detail.put("reason", "dry-run");
            detail.put("toAddress", toAddress);

            String contentSource = NodeConfigReader.text(config, "contentSource", null);
            String templateId = NodeConfigReader.text(config, "templateId", null);

            if (!"MANUAL".equalsIgnoreCase(contentSource) && templateId != null && !templateId.isBlank()) {
                detail.put("contentSource", "VORLAGE");
                try {
                    templateRepository.findById(UUID.fromString(templateId)).ifPresent(template ->
                            detail.put("templateName", template.getName()));
                } catch (Exception e) {
                    log.warn("Failed to resolve template: {}", e.getMessage());
                }
            } else if ("MANUAL".equalsIgnoreCase(contentSource)) {
                detail.put("contentSource", "MANUAL");
                detail.put("renderedSubject", variableResolver.resolve(
                        NodeConfigReader.text(config, "subject", ""), context));
                detail.put("renderedBody", variableResolver.resolve(
                        NodeConfigReader.text(config, "body", ""), context));
            }
            return NodeProcessorResult.followAll(NodeResultStatus.SIMULATED, detail);
        }

        ActionExecutor forwardExecutor = executors.get("FORWARD");
        if (forwardExecutor != null) {
            forwardExecutor.execute(context.getEmail(), context.getAccount(), config, context);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("actionMode", "FORWARD");
        detail.put("toAddress", toAddress);
        return NodeProcessorResult.followAll(NodeResultStatus.EXECUTED, detail);
    }

    private NodeProcessorResult processMove(JsonNode config, ExecutionContext context) throws Exception {
        String folder = NodeConfigReader.text(config, "folder");
        boolean isTrash = "__TRASH__".equals(folder);

        if (context.isDryRun()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("actionMode", "MOVE_FOLDER");
            detail.put("reason", "dry-run");
            detail.put("folder", folder);
            detail.put("isTrash", isTrash);
            return NodeProcessorResult.followAll(NodeResultStatus.SIMULATED, detail);
        }

        ActionExecutor trashExecutor = executors.get("TRASH");
        ActionExecutor moveExecutor = executors.get("MOVE_FOLDER");
        if (isTrash && trashExecutor != null) {
            trashExecutor.execute(context.getEmail(), context.getAccount(), config, context);
        } else if (moveExecutor != null) {
            moveExecutor.execute(context.getEmail(), context.getAccount(), config, context);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("actionMode", "MOVE_FOLDER");
        detail.put("folder", folder);
        detail.put("isTrash", isTrash);
        return NodeProcessorResult.followAll(NodeResultStatus.EXECUTED, detail);
    }
}
