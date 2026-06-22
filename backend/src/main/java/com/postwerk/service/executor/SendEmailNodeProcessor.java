package com.postwerk.service.executor;

import com.postwerk.model.AutomationNode;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.Template;
import com.postwerk.model.enums.NodeResultStatus;
import com.postwerk.model.enums.NodeType;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Processes SEND_EMAIL nodes by composing and sending a new email via SMTP.
 *
 * <p>The sender account is taken from an explicit {@code senderAccountId} in the node config
 * (required when the automation runs without an email trigger, e.g. webhook-triggered), falling
 * back to the trigger account in context. Subject and body can either be supplied inline or
 * sourced from a reusable {@link Template}. Placeholder resolution uses the unified variable
 * system in both cases.</p>
 *
 * @since 1.0
 */
@Component
public class SendEmailNodeProcessor extends AbstractNodeProcessor {

    private final MailSendingSupport mailSendingSupport;
    private final EmailAccountRepository emailAccountRepository;
    private final TemplateRepository templateRepository;
    private final VariableResolver variableResolver;

    public SendEmailNodeProcessor(MailSendingSupport mailSendingSupport,
                                  EmailAccountRepository emailAccountRepository,
                                  TemplateRepository templateRepository,
                                  ObjectMapper objectMapper,
                                  VariableResolver variableResolver) {
        super(objectMapper);
        this.mailSendingSupport = mailSendingSupport;
        this.emailAccountRepository = emailAccountRepository;
        this.templateRepository = templateRepository;
        this.variableResolver = variableResolver;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.SEND_EMAIL;
    }

    /**
     * SEND_EMAIL composes a brand-new message and does not depend on a trigger email, so it can
     * run in automations without an email context (e.g. webhook-triggered). The sender account is
     * resolved from config instead.
     */
    @Override
    public boolean requiresEmailContext() { return false; }

    @Override
    protected NodeProcessorResult doProcess(JsonNode config, AutomationNode node,
                                            ExecutionContext context, UUID userId) throws Exception {
        String to = variableResolver.resolve(NodeConfigReader.text(config, "to"), context);
        String cc = variableResolver.resolve(NodeConfigReader.text(config, "cc"), context);
        String bcc = variableResolver.resolve(NodeConfigReader.text(config, "bcc"), context);

        String subject;
        String body;
        String templateName = null;
        String templateId = NodeConfigReader.text(config, "templateId");
        if (!templateId.isBlank()) {
            UUID orgId = context.getOrganizationId();
            Template template = (orgId != null
                    ? templateRepository.findByIdAndOrganizationId(UUID.fromString(templateId), orgId)
                    : templateRepository.findByIdAndUserId(UUID.fromString(templateId), userId))
                    .orElseThrow(() -> new IllegalStateException("Template not found: " + templateId));
            templateName = template.getName();
            subject = variableResolver.resolve(template.getSubject(), context);
            body = variableResolver.resolve(template.getBody(), context);
        } else {
            subject = variableResolver.resolve(NodeConfigReader.text(config, "subject"), context);
            body = variableResolver.resolve(NodeConfigReader.text(config, "body"), context);
        }

        EmailAccount account = resolveSenderAccount(config, context, userId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("to", to);
        detail.put("cc", cc);
        detail.put("bcc", bcc);
        detail.put("subject", subject);
        detail.put("senderAccount", account.getEmail());
        if (templateName != null) {
            detail.put("templateName", templateName);
        }

        if (context.isDryRun()) {
            detail.put("reason", "dry-run");
            detail.put("resolvedBody", body);
            return NodeProcessorResult.followAll(NodeResultStatus.SIMULATED, detail);
        }

        mailSendingSupport.requireSmtp(account);
        mailSendingSupport.send(account, OutgoingMail.html(to, cc, bcc, subject, body));

        log.info("SEND_EMAIL node {} sent email to {} from {}", node.getId(), to, account.getEmail());
        return NodeProcessorResult.followAll(NodeResultStatus.EXECUTED, detail);
    }

    /**
     * Resolves the sending account: an explicit {@code senderAccountId} (validated against the
     * owning user) takes precedence, otherwise the trigger account from context is used.
     */
    private EmailAccount resolveSenderAccount(JsonNode config, ExecutionContext context, UUID userId) {
        String senderAccountId = NodeConfigReader.text(config, "senderAccountId");
        if (!senderAccountId.isBlank()) {
            UUID orgId = context.getOrganizationId();
            return (orgId != null
                    ? emailAccountRepository.findByIdAndOrganizationId(UUID.fromString(senderAccountId), orgId)
                    : emailAccountRepository.findByIdAndUserId(UUID.fromString(senderAccountId), userId))
                    .orElseThrow(() -> new IllegalStateException("Sender account not found: " + senderAccountId));
        }
        EmailAccount account = context.getAccount();
        if (account == null) {
            throw new IllegalStateException("No sender account configured for SEND_EMAIL node");
        }
        return account;
    }

}
