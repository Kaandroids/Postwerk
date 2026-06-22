package com.postwerk.service.email;

import com.postwerk.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * SMTP-backed {@link EmailSender} using Spring's autoconfigured {@link JavaMailSender}.
 *
 * <p>Wired only when {@code app.mail.enabled=true} (see {@code EmailConfig}); the SMTP host /
 * credentials come from {@code spring.mail.*} — Mailpit in dev, Resend in prod. The same code
 * serves both; only env differs.</p>
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public SmtpEmailSender(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Override
    public void send(EmailMessage message) {
        MimeMessage mime = mailSender.createMimeMessage();
        boolean multipart = message.textBody() != null && !message.textBody().isBlank();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, multipart, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.from(), mailProperties.fromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            if (multipart) {
                helper.setText(message.textBody(), message.htmlBody());
            } else {
                helper.setText(message.htmlBody(), true);
            }
            mailSender.send(mime);
            log.debug("Sent system email to {} (subject: {})", message.to(), message.subject());
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            throw new EmailSendException("Failed to send email to " + message.to(), e);
        }
    }
}
