package com.postwerk.dto;

/** Response DTO for an IMAP/SMTP connection test result. */
public record ConnectionTestResponse(
        boolean success,
        String message
) {}
