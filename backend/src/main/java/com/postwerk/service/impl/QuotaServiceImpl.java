package com.postwerk.service.impl;

import com.postwerk.dto.UsageResponse;
import com.postwerk.exception.QuotaExceededException;
import com.postwerk.model.Plan;
import com.postwerk.model.QuotaOverride;
import com.postwerk.model.enums.QuotaOverrideKind;
import com.postwerk.repository.*;
import com.postwerk.service.PlanCacheService;
import com.postwerk.service.QuotaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.postwerk.util.MonetaryConstants.MICROS_PER_CENT;

/**
 * Default implementation of {@link QuotaService}.
 * Enforces plan-based limits on email accounts, automations, AI token usage, and webhook access.
 *
 * @since 1.0
 */
@Service
public class QuotaServiceImpl implements QuotaService {

    private final EmailAccountRepository emailAccountRepository;
    private final AutomationRepository automationRepository;
    private final AiTokenUsageRepository aiTokenUsageRepository;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final QuotaOverrideRepository quotaOverrideRepository;
    private final PlanCacheService planCacheService;

    public QuotaServiceImpl(EmailAccountRepository emailAccountRepository,
                            AutomationRepository automationRepository,
                            AiTokenUsageRepository aiTokenUsageRepository,
                            WebhookEndpointRepository webhookEndpointRepository,
                            QuotaOverrideRepository quotaOverrideRepository,
                            PlanCacheService planCacheService) {
        this.emailAccountRepository = emailAccountRepository;
        this.automationRepository = automationRepository;
        this.aiTokenUsageRepository = aiTokenUsageRepository;
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.quotaOverrideRepository = quotaOverrideRepository;
        this.planCacheService = planCacheService;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkEmailAccountQuota(UUID organizationId) {
        Plan plan = loadPlan(organizationId);
        int limit = plan.getEmailAccountLimit();
        if (limit == -1) return;

        long current = emailAccountRepository.countByOrganizationId(organizationId);
        if (current >= limit) {
            throw new QuotaExceededException("EMAIL_ACCOUNT", current, limit, plan.getName());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkAutomationQuota(UUID organizationId) {
        Plan plan = loadPlan(organizationId);
        int limit = plan.getAutomationLimit();
        if (limit == -1) return;

        long current = automationRepository.countByOrganizationId(organizationId);
        if (current >= limit) {
            throw new QuotaExceededException("AUTOMATION", current, limit, plan.getName());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkAiTokenQuota(UUID organizationId) {
        Plan plan = loadPlan(organizationId);
        // Effective cap = plan base cap after applying any active per-org/per-user quota overrides.
        // With no overrides this returns plan.getCostLimitCents() unchanged (behavior is identical).
        long costLimit = effectiveCapCents(organizationId, plan.getCostLimitCents());
        if (costLimit == -1) return;
        if (costLimit == 0) {
            throw new QuotaExceededException("AI_COST", 0, 0, plan.getName());
        }

        Instant monthStart = monthStart();
        long usedMicros = aiTokenUsageRepository.sumCostMicrosByOrganizationSince(organizationId, monthStart);
        long limitMicros = costLimit * MICROS_PER_CENT;
        if (usedMicros >= limitMicros) {
            long usedCents = usedMicros / MICROS_PER_CENT;
            throw new QuotaExceededException("AI_COST", usedCents, costLimit, plan.getName());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long effectiveCapCents(UUID organizationId, long basePlanCapCents) {
        List<QuotaOverride> overrides = quotaOverrideRepository.findByOrganizationId(organizationId);
        if (overrides.isEmpty()) {
            return basePlanCapCents; // no overrides → base cap, byte-identical to the no-override path
        }
        Instant now = Instant.now();
        boolean hasUnlimited = false;
        boolean hasCap = false;
        long maxCap = 0L;
        long creditSum = 0L;
        for (QuotaOverride o : overrides) {
            if (!o.isActiveAt(now)) continue; // expired overrides are ignored
            if (o.getKind() == QuotaOverrideKind.UNLIMITED) {
                hasUnlimited = true;
            } else if (o.getKind() == QuotaOverrideKind.CAP) {
                long amount = o.getAmountCents() == null ? 0L : o.getAmountCents();
                if (!hasCap || amount > maxCap) maxCap = amount;
                hasCap = true;
            } else if (o.getKind() == QuotaOverrideKind.CREDIT) {
                creditSum += o.getAmountCents() == null ? 0L : o.getAmountCents();
            }
        }
        if (hasUnlimited) return -1;       // any active UNLIMITED wins → no cap
        if (hasCap) return maxCap;         // most-permissive active CAP replaces the base cap
        if (basePlanCapCents == -1) return -1; // unlimited base stays unlimited (credits don't cap it)
        // CREDIT adds headroom on top of the base cap. A base of 0 (AI disabled) is raised by the credit
        // (an override can re-enable AI). No active overrides at all → creditSum == 0 → base unchanged.
        return basePlanCapCents + creditSum;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkWebhookEnabled(UUID organizationId) {
        Plan plan = loadPlan(organizationId);
        if (!plan.isApiWebhookEnabled()) {
            throw new QuotaExceededException("WEBHOOK", 0, 0, plan.getName());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void checkInboundWebhookQuota(UUID organizationId) {
        Plan plan = loadPlan(organizationId);
        int limit = plan.getInboundWebhookLimit();
        if (limit == -1) return;

        long current = webhookEndpointRepository.countByOrganizationIdAndActiveTrue(organizationId);
        if (current >= limit) {
            throw new QuotaExceededException("INBOUND_WEBHOOK", current, limit, plan.getName());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UsageResponse getUsage(UUID organizationId) {
        Plan plan = loadPlan(organizationId);
        Instant start = monthStart();
        Instant end = monthEnd();

        long tokensUsed = aiTokenUsageRepository.sumTotalTokensByOrganizationSince(organizationId, start);
        long automations = automationRepository.countByOrganizationId(organizationId);
        long emailAccounts = emailAccountRepository.countByOrganizationId(organizationId);

        long costMicros = aiTokenUsageRepository.sumCostMicrosByOrganizationSince(organizationId, start);
        int costUsedCents = (int) (costMicros / MICROS_PER_CENT);

        return new UsageResponse(
                new UsageResponse.PlanInfo(plan.getName(), plan.getTokenLimit(),
                        plan.getAutomationLimit(), plan.getEmailAccountLimit(),
                        plan.isApiWebhookEnabled(), plan.getCostLimitCents()),
                new UsageResponse.UsageInfo(tokensUsed, automations, emailAccounts, costUsedCents, costMicros),
                new UsageResponse.BillingPeriod(start, end)
        );
    }

    private Plan loadPlan(UUID organizationId) {
        return planCacheService.loadPlanForOrg(organizationId);
    }

    private Instant monthStart() {
        return YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant monthEnd() {
        return YearMonth.now(ZoneOffset.UTC).plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
