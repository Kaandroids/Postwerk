package com.postwerk.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a quota-override row (admin "Quota Overrides" list/detail).
 *
 * <p>Per row the service resolves the enforcement org's plan ({@code basePlan}/{@code baseCapCents}),
 * the effective cap ({@code effectiveCapCents}, {@code null} when unlimited), and this-month AI spend
 * ({@code currentSpendCents}). {@code status} is {@code "expired"} when {@code expiresAt <= now}, else
 * {@code "active"} (there is no start-date field, so {@code "scheduled"} is never emitted).
 * {@code targetName}/{@code targetEmailOrSlug}: for a USER target → the user's full name + email; for
 * an ORG target → the org's name + slug.</p>
 *
 * @since 1.0
 */
public record QuotaOverrideResponse(
        UUID id,
        String targetType,
        UUID targetId,
        String targetName,
        String targetEmailOrSlug,
        String basePlan,
        String kind,
        Long amountCents,
        long baseCapCents,
        Long effectiveCapCents,
        long currentSpendCents,
        Instant expiresAt,
        String reason,
        String grantedByName,
        Instant createdAt,
        String status
) {}
