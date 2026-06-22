package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for importing/exporting email templates in bulk.
 *
 * @param name    the template display name
 * @param subject the email subject line with optional parameter placeholders
 * @param body    the email body content with optional parameter placeholders
 */
public record TemplateExportDto(
        @NotBlank(message = "Template name is required") @Size(max = 200) String name,
        @NotBlank(message = "Subject is required") @Size(max = 500) String subject,
        @NotBlank(message = "Body is required") @Size(max = 50000) String body
) {}
