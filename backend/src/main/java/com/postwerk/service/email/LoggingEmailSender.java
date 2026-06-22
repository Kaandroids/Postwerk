package com.postwerk.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback {@link EmailSender} used when no SMTP is configured ({@code app.mail.enabled=false}).
 *
 * <p>Logs the message — including any verification/reset link — at INFO so local development and
 * tests work end-to-end without a mail server. Never used in production (prod sets a real sender).</p>
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        String body = message.textBody() != null ? message.textBody() : message.htmlBody();
        log.info("[EMAIL/no-smtp] to={} | subject={}\n{}", message.to(), message.subject(), body);
    }
}
