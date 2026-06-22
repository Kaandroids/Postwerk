package com.postwerk.dto;

import java.time.Instant;

/** Response DTO for the user's current resource usage and billing period. */
public record UsageResponse(
        PlanInfo plan,
        UsageInfo usage,
        BillingPeriod billingPeriod
) {
    public record PlanInfo(String name, int tokenLimit, int automationLimit,
                           int emailAccountLimit, boolean apiWebhookEnabled,
                           int costLimitCents) {}

    /**
     * @param costUsedCents  AI cost this month truncated to whole cents (legacy field; sub-cent
     *                       usage shows as 0). Prefer {@code costUsedMicros} for accurate percentages.
     * @param costUsedMicros AI cost this month in raw micros (1 cent = 10,000 micros). Lets the UI
     *                       render sub-cent usage as a real percentage instead of always 0%.
     */
    public record UsageInfo(long tokensUsedThisMonth, long activeAutomations,
                            long emailAccounts, int costUsedCents, long costUsedMicros) {}

    public record BillingPeriod(Instant start, Instant end) {}
}
