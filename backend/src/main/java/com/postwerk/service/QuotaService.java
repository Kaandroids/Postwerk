package com.postwerk.service;

import com.postwerk.dto.UsageResponse;

import java.util.UUID;

/**
 * Service interface for enforcing plan-based usage quotas on email accounts,
 * automations, AI token consumption, and webhook access.
 *
 * <p>Quotas are billed per-organization (#4): every check resolves the active organization's
 * plan and counts the organization's resources / AI cost.</p>
 *
 * @since 1.0
 */
public interface QuotaService {

    void checkEmailAccountQuota(UUID organizationId);

    void checkAutomationQuota(UUID organizationId);

    void checkAiTokenQuota(UUID organizationId);

    void checkWebhookEnabled(UUID organizationId);

    void checkInboundWebhookQuota(UUID organizationId);

    UsageResponse getUsage(UUID organizationId);

    /**
     * The org's effective AI cost cap (cents) after applying any active {@code QuotaOverride}s on top of
     * the plan's base cap. Reused by enforcement ({@link #checkAiTokenQuota}) and the admin overrides list.
     *
     * <p>Over the org's active overrides:</p>
     * <ul>
     *   <li>any active {@code UNLIMITED} → {@code -1} (unlimited);</li>
     *   <li>else any active {@code CAP} → the MAX active CAP amount (most-permissive wins);</li>
     *   <li>else {@code basePlanCapCents + sum(active CREDIT amounts)}.</li>
     * </ul>
     * If {@code basePlanCapCents == -1} (unlimited) it stays {@code -1}. If {@code basePlanCapCents == 0}
     * (AI disabled) a CREDIT/CAP override raises it to that amount (an override can re-enable AI). With
     * no active overrides the base cap is returned unchanged.
     *
     * @param organizationId  the enforcement organization
     * @param basePlanCapCents the plan's base monthly cap in cents ({@code -1} unlimited / {@code 0} disabled)
     * @return the effective cap in cents ({@code -1} = unlimited)
     */
    long effectiveCapCents(UUID organizationId, long basePlanCapCents);
}
