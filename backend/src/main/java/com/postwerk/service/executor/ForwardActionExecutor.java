package com.postwerk.service.executor;

import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.Template;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.util.NodeConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Executes the FORWARD action in an automation workflow.
 *
 * <p>Composes a new email with "Fwd:" prefix and the original message body, sends it
 * via SMTP to the configured address, and appends a copy to the SENT folder on IMAP.
 * An optional note can be prepended, sourced either from a saved template (Vorlage) or
 * written manually (Manuell); a custom subject overrides the default "Fwd:" prefix.</p>
 *
 * @since 1.0
 */
@Component
public class ForwardActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ForwardActionExecutor.class);

    private final MailSendingSupport mailSendingSupport;
    private final TemplateRepository templateRepository;
    private final VariableResolver variableResolver;

    public ForwardActionExecutor(MailSendingSupport mailSendingSupport,
                                 TemplateRepository templateRepository, VariableResolver variableResolver) {
        this.mailSendingSupport = mailSendingSupport;
        this.templateRepository = templateRepository;
        this.variableResolver = variableResolver;
    }

    @Override
    public String getActionType() {
        return "FORWARD";
    }

    @Override
    public void execute(Email email, EmailAccount account, JsonNode config, ExecutionContext context) throws Exception {
        mailSendingSupport.requireSmtp(account);

        String toAddress = NodeConfigReader.text(config, "toAddress", null);
        if (toAddress == null || toAddress.isBlank()) {
            throw new IllegalArgumentException("toAddress is required for FORWARD action");
        }

        String noteSubject = resolveNoteSubject(config, context);
        String noteBody = resolveNoteBody(config, context);

        String subject = (noteSubject != null && !noteSubject.isBlank())
                ? noteSubject
                : "Fwd: " + (email.getSubject() != null ? email.getSubject() : "");

        StringBuilder body = new StringBuilder();
        if (noteBody != null && !noteBody.isBlank()) {
            body.append(noteBody).append("\n\n");
        }
        body.append("---------- Forwarded message ----------\n")
                .append("From: ").append(email.getFromAddress() != null ? email.getFromAddress() : "").append("\n")
                .append("Subject: ").append(email.getSubject() != null ? email.getSubject() : "").append("\n\n")
                .append(email.getBodyText() != null ? email.getBodyText() : "");

        mailSendingSupport.send(account, OutgoingMail.plainText(toAddress, subject, body.toString()));

        log.info("Forwarded email {} to {}", email.getId(), toAddress);
    }

    private String resolveNoteSubject(JsonNode config, ExecutionContext context) {
        String contentSource = NodeConfigReader.text(config, "contentSource", null);
        String templateId = NodeConfigReader.text(config, "templateId", null);
        if (!"MANUAL".equalsIgnoreCase(contentSource) && templateId != null && !templateId.isBlank()) {
            Template t = templateRepository.findById(UUID.fromString(templateId)).orElse(null);
            return t != null ? variableResolver.resolve(t.getSubject(), context) : null;
        }
        if ("MANUAL".equalsIgnoreCase(contentSource)) {
            return variableResolver.resolve(NodeConfigReader.text(config, "subject", ""), context);
        }
        return null;
    }

    private String resolveNoteBody(JsonNode config, ExecutionContext context) {
        String contentSource = NodeConfigReader.text(config, "contentSource", null);
        String templateId = NodeConfigReader.text(config, "templateId", null);
        if (!"MANUAL".equalsIgnoreCase(contentSource) && templateId != null && !templateId.isBlank()) {
            Template t = templateRepository.findById(UUID.fromString(templateId)).orElse(null);
            return t != null ? variableResolver.resolve(t.getBody(), context) : null;
        }
        if ("MANUAL".equalsIgnoreCase(contentSource)) {
            return variableResolver.resolve(NodeConfigReader.text(config, "body", ""), context);
        }
        return null;
    }
}
