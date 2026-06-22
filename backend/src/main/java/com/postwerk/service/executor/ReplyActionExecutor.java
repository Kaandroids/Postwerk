package com.postwerk.service.executor;

import com.postwerk.model.Email;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.Template;
import com.postwerk.repository.TemplateRepository;
import com.postwerk.util.NodeConfigReader;
import com.postwerk.util.SafeStrings;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Executes the REPLY_TEMPLATE action in an automation workflow.
 *
 * <p>Loads a user-defined template, resolves parameter placeholders (both built-in
 * email fields and extracted data from upstream EXTRACT nodes), sends the reply via
 * SMTP, and appends a copy to the SENT folder.</p>
 *
 * @since 1.0
 */
@Component
public class ReplyActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReplyActionExecutor.class);

    private final TemplateRepository templateRepository;
    private final MailSendingSupport mailSendingSupport;

    public ReplyActionExecutor(TemplateRepository templateRepository, MailSendingSupport mailSendingSupport) {
        this.templateRepository = templateRepository;
        this.mailSendingSupport = mailSendingSupport;
    }

    @Override
    public String getActionType() {
        return "REPLY_TEMPLATE";
    }

    @Override
    public void execute(Email email, EmailAccount account, JsonNode config, ExecutionContext context) throws Exception {
        mailSendingSupport.requireSmtp(account);

        String contentSource = NodeConfigReader.text(config, "contentSource", null);
        String templateId = NodeConfigReader.text(config, "templateId", null);

        String rawSubject;
        String rawBody;
        String logDetail;

        if (!"MANUAL".equalsIgnoreCase(contentSource) && templateId != null) {
            Template template = templateRepository.findById(UUID.fromString(templateId))
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
            rawSubject = template.getSubject();
            rawBody = template.getBody();
            logDetail = "template " + template.getName();
        } else {
            rawSubject = NodeConfigReader.text(config, "subject", null);
            rawBody = NodeConfigReader.text(config, "body", null);
            if (rawSubject == null && rawBody == null) {
                throw new IllegalArgumentException("REPLY action requires either a templateId or manual subject/body");
            }
            logDetail = "manual content";
        }

        String subject = resolveParams(rawSubject, email, context);
        String body = resolveParams(rawBody, email, context);

        mailSendingSupport.send(account, OutgoingMail.html(email.getFromAddress(), subject, body));
        log.info("Replied to email {} using {}", email.getId(), logDetail);
    }

    private String resolveParams(String text, Email email, ExecutionContext context) {
        if (text == null) return "";
        text = text
                .replace("{{fromAddress}}", SafeStrings.nullToEmpty(email.getFromAddress()))
                .replace("{{fromName}}", SafeStrings.nullToEmpty(email.getFromPersonal()))
                .replace("{{subject}}", SafeStrings.nullToEmpty(email.getSubject()))
                .replace("{{toAddress}}", SafeStrings.nullToEmpty(email.getToAddresses()))
                .replace("{{receivedAt}}", email.getReceivedAt() != null
                        ? email.getReceivedAt().toString() : "");
        if (context != null) {
            Map<String, Object> extracted = context.getAllExtractedData();
            for (Map.Entry<String, Object> entry : extracted.entrySet()) {
                text = text.replace("{{" + entry.getKey() + "}}", SafeStrings.nullToEmpty(String.valueOf(entry.getValue())));
            }
        }
        return text;
    }
}
