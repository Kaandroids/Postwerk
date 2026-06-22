package com.postwerk.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Request DTO for creating or updating a subscription plan. */
public record PlanRequest(
        @NotBlank @Size(max = 100) String name,
        int tokenLimit,
        int automationLimit,
        int emailAccountLimit,
        @PositiveOrZero BigDecimal price,
        boolean apiWebhookEnabled,
        @Min(value = -1, message = "costLimitCents must be -1 (unlimited) or >= 0") int costLimitCents,
        @Min(value = -1, message = "inboundWebhookLimit must be -1 (unlimited) or >= 0") int inboundWebhookLimit,
        /** Nullable: when omitted on update the existing value is preserved; on create defaults to true. */
        Boolean marketplacePublishEnabled
) {}
