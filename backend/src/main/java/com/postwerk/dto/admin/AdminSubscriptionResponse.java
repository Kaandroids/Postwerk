package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * One organization's subscription row in the admin Plans &amp; Subscriptions list.
 *
 * <p>{@code effectiveCapCents} is the org's AI cost cap after applying any active quota overrides
 * ({@code -1} = unlimited, {@code 0} = AI disabled, {@code >0} = monthly cap in cents).
 * {@code status} is {@code active} | {@code suspended}.</p>
 */
public record AdminSubscriptionResponse(
        UUID orgId,
        String orgName,
        String slug,
        boolean personal,
        String ownerName,
        String ownerEmail,
        String planName,
        String status,
        long memberCount,
        long mailboxCount,
        long automationCount,
        long aiCostMicrosThisMonth,
        long effectiveCapCents,
        Instant createdAt
) {}
