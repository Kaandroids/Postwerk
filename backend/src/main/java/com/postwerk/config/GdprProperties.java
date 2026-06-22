package com.postwerk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration properties for GDPR data retention policies.
 *
 * <p>Bound from {@code app.gdpr.*} in {@code application.yml}. Controls retention
 * periods for emails, audit logs, and the grace period before permanent account deletion.
 * Defaults are applied for any non-positive values.</p>
 *
 * @since 1.0
 */
@ConfigurationProperties(prefix = "app.gdpr")
public record GdprProperties(
        int emailRetentionDays,
        int auditLogRetentionDays,
        int accountDeletionGraceDays,
        int conversationRetentionDays,
        int ipRetentionDays
) {
    public GdprProperties {
        if (emailRetentionDays <= 0) emailRetentionDays = 365;
        if (auditLogRetentionDays <= 0) auditLogRetentionDays = 730;
        if (accountDeletionGraceDays <= 0) accountDeletionGraceDays = 30;
        if (conversationRetentionDays <= 0) conversationRetentionDays = 90;
        if (ipRetentionDays <= 0) ipRetentionDays = 90;
    }
}
