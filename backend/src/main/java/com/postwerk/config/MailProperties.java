package com.postwerk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for system/transactional email.
 *
 * <p>Bound from {@code app.mail.*} in {@code application.yml}. The SMTP connection itself is
 * configured under {@code spring.mail.*}; this holds only the sender identity used on every
 * outgoing message.</p>
 *
 * @param from     the envelope/from address, e.g. {@code noreply@postwerk.io}
 * @param fromName the display name shown to recipients, e.g. {@code Postwerk}
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        String from,
        String fromName
) {}
