package com.postwerk.config;

import com.postwerk.service.email.EmailSender;
import com.postwerk.service.email.LoggingEmailSender;
import com.postwerk.service.email.SmtpEmailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Selects the active {@link EmailSender}:
 * <ul>
 *   <li>{@code app.mail.enabled=true} → {@link SmtpEmailSender} (real SMTP via {@code spring.mail.*}).</li>
 *   <li>otherwise → {@link LoggingEmailSender} (logs the message; keeps tests &amp; no-mail dev running).</li>
 * </ul>
 * Declaration order matters: the SMTP bean is defined first so {@code @ConditionalOnMissingBean}
 * on the fallback resolves correctly within this configuration class.
 */
@Configuration
public class EmailConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
    public EmailSender smtpEmailSender(JavaMailSender mailSender, MailProperties mailProperties) {
        return new SmtpEmailSender(mailSender, mailProperties);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }
}
