package com.postwerk.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full subscription detail for the Plans &amp; Subscriptions detail modal: the org + its current plan,
 * effective AI cap (resolving overrides), this-month metered AI cost, usage-vs-limit counts, and the
 * plan-change history.
 */
public record AdminSubscriptionDetailResponse(
        UUID orgId,
        String orgName,
        String slug,
        boolean personal,
        String ownerName,
        String ownerEmail,
        String status,
        UUID planId,
        String planName,
        BigDecimal planPrice,
        int planCostLimitCents,        // base plan cap (-1/0/>0)
        long effectiveCapCents,        // after overrides
        long aiCostMicrosThisMonth,
        long memberCount,
        long mailboxCount,
        long automationCount,
        int mailboxLimit,              // plan emailAccountLimit (-1 = unlimited)
        int automationLimit,           // plan automationLimit (-1 = unlimited)
        Instant createdAt,
        Instant suspendedAt,
        List<PlanHistoryEntryResponse> planHistory
) {}
