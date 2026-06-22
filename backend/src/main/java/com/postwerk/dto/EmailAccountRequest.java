package com.postwerk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Request DTO for creating or updating an email account with IMAP/SMTP settings. */
public record EmailAccountRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 100) String displayName,
        @NotBlank @Size(max = 20) String color,
        boolean readEnabled,
        boolean writeEnabled,
        @Size(max = 253) String imapHost,
        @Min(1) @Max(65535) Integer imapPort,
        @Size(max = 320) String imapUsername,
        @Size(max = 256) String imapPassword,
        Boolean imapSsl,
        @Size(max = 253) String smtpHost,
        @Min(1) @Max(65535) Integer smtpPort,
        @Size(max = 320) String smtpUsername,
        @Size(max = 256) String smtpPassword,
        Boolean smtpSsl,
        LocalDate syncFromDate,
        boolean isDefault
) {}
