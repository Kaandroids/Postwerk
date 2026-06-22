package com.postwerk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTO for an email template with placeholders and linked parameter set. */
public record TemplateResponse(
        UUID id,
        String name,
        String subject,
        String body,
        List<String> params,
        UUID parameterSetId,
        String parameterSetName,
        boolean locked,
        Instant createdAt,
        Instant updatedAt
) {}
