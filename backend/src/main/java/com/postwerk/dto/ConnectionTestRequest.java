package com.postwerk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request payload for testing IMAP or SMTP mail server connectivity.
 *
 * @param host     the mail server hostname (e.g. imap.gmail.com)
 * @param port     the server port (1–65535)
 * @param username the login username
 * @param password the login password
 * @param ssl      whether to use SSL/TLS
 * @param type     the protocol to test — must be {@code "imap"} or {@code "smtp"}
 */
public record ConnectionTestRequest(
        @NotBlank(message = "Host is required") String host,
        @Min(value = 1, message = "Port must be between 1 and 65535")
        @Max(value = 65535, message = "Port must be between 1 and 65535") int port,
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required") String password,
        boolean ssl,
        @NotBlank(message = "Type is required")
        @Pattern(regexp = "^(imap|smtp)$", message = "Type must be 'imap' or 'smtp'") String type
) {}
