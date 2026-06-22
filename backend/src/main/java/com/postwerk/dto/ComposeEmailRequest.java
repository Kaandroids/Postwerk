package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for composing or saving an email draft. */
public record ComposeEmailRequest(
    @NotBlank String to,
    String cc,
    String bcc,
    @NotBlank String subject,
    String body,
    String inReplyTo,
    String replyToEmailId,
    boolean isDraft
) {}
