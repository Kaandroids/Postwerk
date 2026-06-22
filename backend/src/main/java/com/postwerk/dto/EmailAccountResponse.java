package com.postwerk.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Response DTO for an email account with connection settings. */
public record EmailAccountResponse(
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
        LocalDate syncFromDate,
        boolean isDefault,
        boolean isActive,
        Instant createdAt
) {}
