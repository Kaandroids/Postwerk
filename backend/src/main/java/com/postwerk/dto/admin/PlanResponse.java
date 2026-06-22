package com.postwerk.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Response DTO for a subscription plan with all quota limits. */
public record PlanResponse(
        UUID id,
        String name,
        int tokenLimit,
        int automationLimit,
        int emailAccountLimit,
        BigDecimal price,
        boolean apiWebhookEnabled,
        int costLimitCents,
        int inboundWebhookLimit,
        boolean marketplacePublishEnabled,
        boolean isDefault,
        long userCount,
        Instant createdAt
) {}
