package com.postwerk.dto.admin;

import java.math.BigDecimal;

/**
 * KPI strip totals for the Plans &amp; Subscriptions header, all derived from the subscription list.
 *
 * <p>{@code mrr} is metadata-only / derived: plan price summed over active (non-suspended)
 * subscriptions — there is no payment processor. {@code aiCostCentsThisMonth} is real metered spend.</p>
 */
public record SubscriptionKpisResponse(
        BigDecimal mrr,
        long activeSubscriptions,
        long aiCostCentsThisMonth,
        long overCapCount,
        long planCount
) {}
