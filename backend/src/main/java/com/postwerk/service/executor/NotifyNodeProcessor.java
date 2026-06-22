package com.postwerk.service.executor;

import com.postwerk.dto.automation.NodeMock;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.Template;
import com.postwerk.model.enums.MembershipStatus;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.model.enums.NotificationCategory;
import com.postwerk.model.enums.NotificationSeverity;
import com.postwerk.model.enums.NotificationType;
import com.postwerk.model.enums.OrgRole;
import com.postwerk.repository.MembershipRepository;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.service.NewNotification;
import com.postwerk.service.NotificationService;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Processes the {@code NOTIFY} node — sends an in-app/email notification to a member of the
 * automation's own organization. Title/message support variable interpolation; the recipient is
 * validated as an active member at runtime (membership can change after the flow was built). Routes
 * {@code success}/{@code fail} and injects {@code notify_<nodeId>.sent} / {@code .recipientCount}.
 * Mockable in dry-run/tests like the other side-effect nodes. See {@code doc/NOTIFICATION_SYSTEM_DESIGN.md}.
 *
 * @since 1.0
 */
@Component
public class NotifyNodeProcessor extends AbstractNodeProcessor {

    private final VariableResolver variableResolver;
    private final NotificationService notificationService;
    private final MembershipRepository membershipRepository;
    private final TemplateRepository templateRepository;

    public NotifyNodeProcessor(ObjectMapper objectMapper,
                               VariableResolver variableResolver,
                               NotificationService notificationService,
                               MembershipRepository membershipRepository,
                               TemplateRepository templateRepository) {
        super(objectMapper);
        this.variableResolver = variableResolver;
        this.notificationService = notificationService;
        this.membershipRepository = membershipRepository;
        this.templateRepository = templateRepository;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.NOTIFY;
    }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) {
        NodeMock mock = context.getMock(node.getId());
        if (mock != null && mock.isMock()) {
            return processMock(node, context, mock);
        }

        // Content comes from a saved template (subject → title, body → message) or inline title/message —
        // resolved the same way SEND_EMAIL chooses, by whether a templateId is set.
        String templateId = NodeConfigReader.text(config, "templateId", "");
        String title;
        String message;
        if (!templateId.isBlank()) {
            Template template = loadTemplate(templateId, context.getOrganizationId(), userId);
            title = variableResolver.resolve(template.getSubject() == null ? "" : template.getSubject(), context).trim();
            message = htmlToText(variableResolver.resolve(template.getBody() == null ? "" : template.getBody(), context)).trim();
        } else {
            title = variableResolver.resolve(NodeConfigReader.text(config, "title", ""), context).trim();
            message = variableResolver.resolve(NodeConfigReader.text(config, "message", ""), context).trim();
        }
        if (title.isBlank() && message.isBlank()) {
            return NodeProcessorResult.byHandle(NodeResultStatus.ERROR,
                    Map.of("error", "Notify node has no message"), Set.of("fail"));
        }

        List<UUID> recipients = resolveRecipients(config, context.getOrganizationId());
        if (recipients.isEmpty()) {
            return NodeProcessorResult.byHandle(NodeResultStatus.ERROR,
                    Map.of("error", "No valid recipient in this organization", "title", title, "message", message),
                    Set.of("fail"));
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("recipientCount", recipients.size());
        detail.put("title", title);
        detail.put("message", message);

        if (context.isDryRun()) {
            detail.put("simulated", true);
            return success(node, context, detail, recipients.size(), NodeResultStatus.SIMULATED);
        }

        NotificationSeverity severity = parseSeverity(config);
        NotificationCategory category = parseCategory(config);
        String linkUrl = emptyToNull(NodeConfigReader.text(config, "linkUrl", null));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", title);
        params.put("message", message);

        notificationService.create(recipients, new NewNotification(
                NotificationType.NOTIFY_NODE, context.getOrganizationId(), severity, category,
                params, linkUrl, Map.of("automationNode", node.getId().toString()), null));

        return success(node, context, detail, recipients.size(), NodeResultStatus.EXECUTED);
    }

    private NodeProcessorResult processMock(AutomationNode node, ExecutionContext context, NodeMock mock) {
        if (mock.shouldForceError()) {
            return NodeProcessorResult.byHandle(NodeResultStatus.ERROR,
                    Map.of("mocked", true, "error", "Mocked notify failure"), Set.of("fail"));
        }
        Map<String, Object> response = mock.response() != null ? mock.response() : Map.of();
        int count = response.get("recipientCount") instanceof Number n ? n.intValue() : 1;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mocked", true);
        detail.put("recipientCount", count);
        return success(node, context, detail, count, NodeResultStatus.SIMULATED);
    }

    /** Injects {@code notify_<key>.sent}/{@code .recipientCount} and routes the {@code success} handle. */
    private NodeProcessorResult success(AutomationNode node, ExecutionContext context,
                                        Map<String, Object> detail, int recipientCount, NodeResultStatus status) {
        String prefix = varPrefix(node);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put(prefix + "sent", true);
        vars.put(prefix + "recipientCount", recipientCount);
        ExecutionContext enriched = context.withVariables(vars);
        return NodeProcessorResult.byHandleWithContext(status, detail, Set.of("success"), Map.of("success", enriched));
    }

    private List<UUID> resolveRecipients(JsonNode config, UUID organizationId) {
        if (organizationId == null) return List.of();
        String type = NodeConfigReader.text(config, "recipientType", "USER");
        return switch (type) {
            case "OWNER" -> membershipRepository.findActiveUserIdsByOrgAndRole(organizationId, OrgRole.OWNER);
            case "ADMINS" -> membershipRepository.findActiveAdminUserIds(organizationId);
            case "USER" -> {
                String uid = NodeConfigReader.text(config, "recipientUserId", null);
                if (uid == null || uid.isBlank()) yield List.of();
                UUID candidate;
                try {
                    candidate = UUID.fromString(uid);
                } catch (IllegalArgumentException e) {
                    yield List.of();
                }
                boolean active = membershipRepository.findByOrganizationIdAndUserId(organizationId, candidate)
                        .filter(m -> m.getStatus() == MembershipStatus.ACTIVE).isPresent();
                yield active ? List.of(candidate) : List.of();
            }
            default -> List.of();
        };
    }

    private NotificationSeverity parseSeverity(JsonNode config) {
        try {
            return NotificationSeverity.valueOf(NodeConfigReader.text(config, "severity", "INFO"));
        } catch (IllegalArgumentException e) {
            return NotificationSeverity.INFO;
        }
    }

    private NotificationCategory parseCategory(JsonNode config) {
        try {
            return NotificationCategory.valueOf(NodeConfigReader.text(config, "category", "SYSTEM"));
        } catch (IllegalArgumentException e) {
            return NotificationCategory.SYSTEM;
        }
    }

    private Template loadTemplate(String templateId, UUID organizationId, UUID userId) {
        return (organizationId != null
                ? templateRepository.findByIdAndOrganizationId(UUID.fromString(templateId), organizationId)
                : templateRepository.findByIdAndUserId(UUID.fromString(templateId), userId))
                .orElseThrow(() -> new IllegalStateException("Template not found: " + templateId));
    }

    /** Flattens a template's HTML body into plain text for the notification message (notifications render text). */
    private static String htmlToText(String html) {
        if (html == null || html.isEmpty()) return "";
        return html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String varPrefix(AutomationNode node) {
        String key = (node.getNodeKey() != null && !node.getNodeKey().isBlank())
                ? node.getNodeKey() : node.getId().toString();
        return "notify_" + key + ".";
    }
}
