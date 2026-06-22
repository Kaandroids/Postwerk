package com.postwerk.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Response DTO for the authenticated user's current plan and quota limits. */
public record PlanSummaryResponse(
        UUID id,
        String name,
        int tokenLimit,
        int automationLimit,
        int emailAccountLimit,
        BigDecimal price,
        boolean apiWebhookEnabled,
        int costLimitCents
) {}
