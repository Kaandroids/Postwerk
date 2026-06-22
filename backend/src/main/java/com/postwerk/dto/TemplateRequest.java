package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request DTO for creating or updating an email template. */
public record TemplateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank String subject,
        @NotBlank String body,
        UUID parameterSetId
) {}
