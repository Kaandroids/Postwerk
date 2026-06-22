package com.postwerk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTO for GDPR data export containing all user-owned data. */
public record UserExportResponse(
        ProfileExport profile,
        List<AccountExport> emailAccounts,
        List<EmailExport> emails,
        List<CategoryExport> categories,
        List<FilterExport> filters,
        List<AuditLogExport> auditLogs,
        Instant exportedAt
) {
    public record ProfileExport(
            UUID id,
            String email,
            String fullName,
            String company,
            String phone,
            boolean marketingOptIn,
            Instant privacyAcceptedAt,
            Instant termsAcceptedAt,
            String privacyVersion,
            Instant createdAt
    ) {}

    public record AccountExport(
            UUID id,
            String email,
            String displayName,
            String color,
            boolean readEnabled,
            boolean writeEnabled,
            String imapHost,
            Integer imapPort,
            String smtpHost,
            Integer smtpPort,
            Instant createdAt
    ) {}

    public record EmailExport(
            UUID id,
            UUID emailAccountId,
            String folder,
            String fromAddress,
            String toAddresses,
            String subject,
            String snippet,
            Instant receivedAt,
            boolean isRead,
            boolean isStarred
    ) {}

    public record CategoryExport(
            UUID id,
            String name,
            String color,
            String description,
            Instant createdAt
    ) {}

    public record FilterExport(
            UUID id,
            String name,
            String color,
            String description,
            Instant createdAt
    ) {}

    public record AuditLogExport(
            UUID id,
            String action,
            String detail,
            String ipAddress,
            Instant createdAt
    ) {}
}
