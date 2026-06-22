package com.postwerk.service;

import com.postwerk.dto.UsageResponse;
import com.postwerk.exception.QuotaExceededException;
import com.postwerk.model.Plan;
import com.postwerk.model.QuotaOverride;
import com.postwerk.model.enums.QuotaOverrideKind;
import com.postwerk.model.enums.QuotaOverrideTargetType;
import com.postwerk.repository.AiTokenUsageRepository;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.QuotaOverrideRepository;
import com.postwerk.repository.WebhookEndpointRepository;
import com.postwerk.service.impl.QuotaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private AutomationRepository automationRepository;
    @Mock private AiTokenUsageRepository aiTokenUsageRepository;
    @Mock private WebhookEndpointRepository webhookEndpointRepository;
    @Mock private QuotaOverrideRepository quotaOverrideRepository;
    @Mock private PlanCacheService planCacheService;

    @InjectMocks
    private QuotaServiceImpl quotaService;

    private UUID orgId;
    private Plan plan;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        plan = Plan.builder()
                .id(UUID.randomUUID())
                .name("PRO")
                .tokenLimit(100000)
                .automationLimit(10)
                .emailAccountLimit(5)
                .price(new BigDecimal("29.00"))
                .apiWebhookEnabled(true)
                .costLimitCents(500)
                .inboundWebhookLimit(3)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // --- checkEmailAccountQuota ---

    @Test
    void checkEmailAccountQuota_underLimit_doesNotThrow() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(emailAccountRepository.countByOrganizationId(orgId)).thenReturn(3L);

        assertThatCode(() -> quotaService.checkEmailAccountQuota(orgId))
                .doesNotThrowAnyException();
    }

    @Test
    void checkEmailAccountQuota_atLimit_throwsQuotaExceeded() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(emailAccountRepository.countByOrganizationId(orgId)).thenReturn(5L);

        assertThatThrownBy(() -> quotaService.checkEmailAccountQuota(orgId))
                .isInstanceOf(QuotaExceededException.class)
                .satisfies(ex -> {
                    QuotaExceededException qe = (QuotaExceededException) ex;
                    assertThat(qe.getLimitType()).isEqualTo("EMAIL_ACCOUNT");
                    assertThat(qe.getCurrentUsage()).isEqualTo(5L);
                    assertThat(qe.getMaxAllowed()).isEqualTo(5L);
                    assertThat(qe.getPlanName()).isEqualTo("PRO");
                });
    }

    @Test
    void checkEmailAccountQuota_overLimit_throwsQuotaExceeded() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(emailAccountRepository.countByOrganizationId(orgId)).thenReturn(7L);

        assertThatThrownBy(() -> quotaService.checkEmailAccountQuota(orgId))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void checkEmailAccountQuota_unlimitedPlan_doesNotThrow() {
        plan.setEmailAccountLimit(-1);
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);

        assertThatCode(() -> quotaService.checkEmailAccountQuota(orgId))
                .doesNotThrowAnyException();

        verify(emailAccountRepository, never()).countByOrganizationId(any());
    }

    // --- checkAutomationQuota ---

    @Test
    void checkAutomationQuota_underLimit_doesNotThrow() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(automationRepository.countByOrganizationId(orgId)).thenReturn(5L);

        assertThatCode(() -> quotaService.checkAutomationQuota(orgId))
                .doesNotThrowAnyException();
    }

    @Test
    void checkAutomationQuota_atLimit_throwsQuotaExceeded() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(automationRepository.countByOrganizationId(orgId)).thenReturn(10L);

        assertThatThrownBy(() -> quotaService.checkAutomationQuota(orgId))
                .isInstanceOf(QuotaExceededException.class)
                .satisfies(ex -> {
                    QuotaExceededException qe = (QuotaExceededException) ex;
                    assertThat(qe.getLimitType()).isEqualTo("AUTOMATION");
                    assertThat(qe.getCurrentUsage()).isEqualTo(10L);
                    assertThat(qe.getMaxAllowed()).isEqualTo(10L);
                    assertThat(qe.getPlanName()).isEqualTo("PRO");
                });
    }

    @Test
    void checkAutomationQuota_overLimit_throwsQuotaExceeded() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(automationRepository.countByOrganizationId(orgId)).thenReturn(15L);

        assertThatThrownBy(() -> quotaService.checkAutomationQuota(orgId))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void checkAutomationQuota_unlimitedPlan_doesNotThrow() {
        plan.setAutomationLimit(-1);
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);

        assertThatCode(() -> quotaService.checkAutomationQuota(orgId))
                .doesNotThrowAnyException();

        verify(automationRepository, never()).countByOrganizationId(any());
    }

    // --- checkInboundWebhookQuota ---

    @Test
    void checkInboundWebhookQuota_underLimit_doesNotThrow() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(webhookEndpointRepository.countByOrganizationIdAndActiveTrue(orgId)).thenReturn(1L);

        assertThatCode(() -> quotaService.checkInboundWebhookQuota(orgId))
                .doesNotThrowAnyException();
    }

    @Test
    void checkInboundWebhookQuota_atLimit_throwsQuotaExceeded() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(webhookEndpointRepository.countByOrganizationIdAndActiveTrue(orgId)).thenReturn(3L);

        assertThatThrownBy(() -> quotaService.checkInboundWebhookQuota(orgId))
                .isInstanceOf(QuotaExceededException.class)
                .satisfies(ex -> {
                    QuotaExceededException qe = (QuotaExceededException) ex;
                    assertThat(qe.getLimitType()).isEqualTo("INBOUND_WEBHOOK");
                    assertThat(qe.getCurrentUsage()).isEqualTo(3L);
                    assertThat(qe.getMaxAllowed()).isEqualTo(3L);
                    assertThat(qe.getPlanName()).isEqualTo("PRO");
                });
    }

    @Test
    void checkInboundWebhookQuota_overLimit_throwsQuotaExceeded() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(webhookEndpointRepository.countByOrganizationIdAndActiveTrue(orgId)).thenReturn(5L);

        assertThatThrownBy(() -> quotaService.checkInboundWebhookQuota(orgId))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void checkInboundWebhookQuota_disabledPlan_zeroLimit_throwsQuotaExceeded() {
        plan.setInboundWebhookLimit(0);
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(webhookEndpointRepository.countByOrganizationIdAndActiveTrue(orgId)).thenReturn(0L);

        assertThatThrownBy(() -> quotaService.checkInboundWebhookQuota(orgId))
                .isInstanceOf(QuotaExceededException.class)
                .satisfies(ex -> {
                    QuotaExceededException qe = (QuotaExceededException) ex;
                    assertThat(qe.getLimitType()).isEqualTo("INBOUND_WEBHOOK");
                });
    }

    @Test
    void checkInboundWebhookQuota_unlimitedPlan_doesNotThrow() {
        plan.setInboundWebhookLimit(-1);
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);

        assertThatCode(() -> quotaService.checkInboundWebhookQuota(orgId))
                .doesNotThrowAnyException();

        verify(webhookEndpointRepository, never()).countByOrganizationIdAndActiveTrue(any());
    }

    // --- checkAiTokenQuota ---

    @Test
    void checkAiTokenQuota_underCostLimit_doesNotThrow() {
        plan.setCostLimitCents(500); // 500 cents = 5_000_000 micros
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(aiTokenUsageRepository.sumCostMicrosByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(2_000_000L); // 200 cents used

        assertThatCode(() -> quotaService.checkAiTokenQuota(orgId))
                .doesNotThrowAnyException();
    }

    @Test
    void checkAiTokenQuota_atCostLimit_throwsQuotaExceeded() {
        plan.setCostLimitCents(500); // limit = 5_000_000 micros
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(aiTokenUsageRepository.sumCostMicrosByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(5_000_000L); // exactly at limit

        assertThatThrownBy(() -> quotaService.checkAiTokenQuota(orgId))
                .isInstanceOf(QuotaExceededException.class)
                .satisfies(ex -> {
                    QuotaExceededException qe = (QuotaExceededException) ex;
                    assertThat(qe.getLimitType()).isEqualTo("AI_COST");
                    assertThat(qe.getCurrentUsage()).isEqualTo(500L);
                    assertThat(qe.getMaxAllowed()).isEqualTo(500L);
                    assertThat(qe.getPlanName()).isEqualTo("PRO");
                });
    }

    @Test
    void checkAiTokenQuota_overCostLimit_throwsQuotaExceeded() {
        plan.setCostLimitCents(500);
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(aiTokenUsageRepository.sumCostMicrosByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(7_500_000L); // 750 cents > 500

        assertThatThrownBy(() -> quotaService.checkAiTokenQuota(orgId))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void checkAiTokenQuota_costLimitZero_aiDisabled_throwsQuotaExceeded() {
        plan.setCostLimitCents(0);
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);

        assertThatThrownBy(() -> quotaService.checkAiTokenQuota(orgId))
                .isInstanceOf(QuotaExceededException.class)
                .satisfies(ex -> {
                    QuotaExceededException qe = (QuotaExceededException) ex;
                    assertThat(qe.getLimitType()).isEqualTo("AI_COST");
                    assertThat(qe.getCurrentUsage()).isEqualTo(0L);
                    assertThat(qe.getMaxAllowed()).isEqualTo(0L);
                    assertThat(qe.getPlanName()).isEqualTo("PRO");
                });

        verify(aiTokenUsageRepository, never()).sumCostMicrosByOrganizationSince(any(), any());
    }

    @Test
    void checkAiTokenQuota_costLimitUnlimited_doesNotThrow() {
        plan.setCostLimitCents(-1);
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);

        assertThatCode(() -> quotaService.checkAiTokenQuota(orgId))
                .doesNotThrowAnyException();

        verify(aiTokenUsageRepository, never()).sumCostMicrosByOrganizationSince(any(), any());
    }

    // --- effectiveCapCents (quota overrides) ---

    private static QuotaOverride override(QuotaOverrideKind kind, Long amountCents, Instant expiresAt) {
        return QuotaOverride.builder()
                .id(UUID.randomUUID())
                .targetType(QuotaOverrideTargetType.ORG)
                .targetId(UUID.randomUUID())
                .organizationId(UUID.randomUUID())
                .kind(kind)
                .amountCents(amountCents)
                .expiresAt(expiresAt)
                .reason("test")
                .build();
    }

    @Test
    void effectiveCap_noOverrides_returnsBaseCapUnchanged() {
        when(quotaOverrideRepository.findByOrganizationId(orgId)).thenReturn(List.of());

        assertThat(quotaService.effectiveCapCents(orgId, 500)).isEqualTo(500);
        assertThat(quotaService.effectiveCapCents(orgId, -1)).isEqualTo(-1);
        assertThat(quotaService.effectiveCapCents(orgId, 0)).isEqualTo(0);
    }

    @Test
    void effectiveCap_credit_addsHeadroomOnTopOfBase() {
        when(quotaOverrideRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(override(QuotaOverrideKind.CREDIT, 300L, null)));

        assertThat(quotaService.effectiveCapCents(orgId, 500)).isEqualTo(800);
    }

    @Test
    void effectiveCap_creditsSum() {
        when(quotaOverrideRepository.findByOrganizationId(orgId)).thenReturn(List.of(
                override(QuotaOverrideKind.CREDIT, 300L, null),
                override(QuotaOverrideKind.CREDIT, 200L, null)));

        assertThat(quotaService.effectiveCapCents(orgId, 500)).isEqualTo(1000);
    }

    @Test
    void effectiveCap_creditOnDisabledBase_reEnablesAi() {
        when(quotaOverrideRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(override(QuotaOverrideKind.CREDIT, 250L, null)));

        // base 0 (AI disabled) + 250 credit → 250 (AI re-enabled at that cap)
        assertThat(quotaService.effectiveCapCents(orgId, 0)).isEqualTo(250);
    }

    @Test
    void effectiveCap_cap_replacesBase_mostPermissiveWins() {
        when(quotaOverrideRepository.findByOrganizationId(orgId)).thenReturn(List.of(
                override(QuotaOverrideKind.CAP, 200L, null),
                override(QuotaOverrideKind.CAP, 900L, null),
                override(QuotaOverrideKind.CREDIT, 999L, null))); // CAP outranks CREDIT

        assertThat(quotaService.effectiveCapCents(orgId, 500)).isEqualTo(900);
    }

    @Test
    void effectiveCap_unlimited_bypassesEverything() {
        when(quotaOverrideRepository.findByOrganizationId(orgId)).thenReturn(List.of(
                override(QuotaOverrideKind.CAP, 100L, null),
                override(QuotaOverrideKind.UNLIMITED, null, null)));

        assertThat(quotaService.effectiveCapCents(orgId, 500)).isEqualTo(-1);
    }

    @Test
    void effectiveCap_expiredOverrides_ignored() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        when(quotaOverrideRepository.findByOrganizationId(orgId)).thenReturn(List.of(
                override(QuotaOverrideKind.UNLIMITED, null, past),
                override(QuotaOverrideKind.CREDIT, 300L, past)));

        // all expired → base cap unchanged
        assertThat(quotaService.effectiveCapCents(orgId, 500)).isEqualTo(500);
    }

    @Test
    void checkAiTokenQuota_creditOverride_raisesCap_allowsSpendOverBasePlan() {
        plan.setCostLimitCents(500); // base = 5_000_000 micros
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        // active credit adds 300c → effective 800c = 8_000_000 micros
        when(quotaOverrideRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(override(QuotaOverrideKind.CREDIT, 300L, null)));
        when(aiTokenUsageRepository.sumCostMicrosByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(6_000_000L); // 600c — over base 500c, under effective 800c

        assertThatCode(() -> quotaService.checkAiTokenQuota(orgId))
                .doesNotThrowAnyException();
    }

    @Test
    void checkAiTokenQuota_unlimitedOverride_bypassesCap() {
        plan.setCostLimitCents(500);
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(quotaOverrideRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(override(QuotaOverrideKind.UNLIMITED, null, null)));

        assertThatCode(() -> quotaService.checkAiTokenQuota(orgId))
                .doesNotThrowAnyException();
        // unlimited short-circuits before any usage lookup
        verify(aiTokenUsageRepository, never()).sumCostMicrosByOrganizationSince(any(), any());
    }

    @Test
    void checkAiTokenQuota_creditOnDisabledPlan_reEnablesAi() {
        plan.setCostLimitCents(0); // AI disabled
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(quotaOverrideRepository.findByOrganizationId(orgId))
                .thenReturn(List.of(override(QuotaOverrideKind.CREDIT, 200L, null)));
        when(aiTokenUsageRepository.sumCostMicrosByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(500_000L); // 50c, under effective 200c

        assertThatCode(() -> quotaService.checkAiTokenQuota(orgId))
                .doesNotThrowAnyException();
    }

    // --- checkWebhookEnabled ---

    @Test
    void checkWebhookEnabled_planAllowsWebhooks_doesNotThrow() {
        plan.setApiWebhookEnabled(true);
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);

        assertThatCode(() -> quotaService.checkWebhookEnabled(orgId))
                .doesNotThrowAnyException();
    }

    @Test
    void checkWebhookEnabled_planDisallowsWebhooks_throwsQuotaExceeded() {
        plan.setApiWebhookEnabled(false);
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);

        assertThatThrownBy(() -> quotaService.checkWebhookEnabled(orgId))
                .isInstanceOf(QuotaExceededException.class)
                .satisfies(ex -> {
                    QuotaExceededException qe = (QuotaExceededException) ex;
                    assertThat(qe.getLimitType()).isEqualTo("WEBHOOK");
                    assertThat(qe.getPlanName()).isEqualTo("PRO");
                });
    }

    // --- getUsage ---

    @Test
    void getUsage_returnsCorrectUsageResponse() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(aiTokenUsageRepository.sumTotalTokensByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(5000L);
        when(automationRepository.countByOrganizationId(orgId)).thenReturn(3L);
        when(emailAccountRepository.countByOrganizationId(orgId)).thenReturn(2L);
        when(aiTokenUsageRepository.sumCostMicrosByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(1_250_000L); // 125 cents

        UsageResponse response = quotaService.getUsage(orgId);

        // Plan info
        assertThat(response.plan().name()).isEqualTo("PRO");
        assertThat(response.plan().tokenLimit()).isEqualTo(100000);
        assertThat(response.plan().automationLimit()).isEqualTo(10);
        assertThat(response.plan().emailAccountLimit()).isEqualTo(5);
        assertThat(response.plan().apiWebhookEnabled()).isTrue();
        assertThat(response.plan().costLimitCents()).isEqualTo(500);

        // Usage info
        assertThat(response.usage().tokensUsedThisMonth()).isEqualTo(5000L);
        assertThat(response.usage().activeAutomations()).isEqualTo(3L);
        assertThat(response.usage().emailAccounts()).isEqualTo(2L);
        assertThat(response.usage().costUsedCents()).isEqualTo(125);
        assertThat(response.usage().costUsedMicros()).isEqualTo(1_250_000L);

        // Billing period
        assertThat(response.billingPeriod().start()).isNotNull();
        assertThat(response.billingPeriod().end()).isNotNull();
        assertThat(response.billingPeriod().end()).isAfter(response.billingPeriod().start());
    }

    @Test
    void getUsage_noUsage_returnsZeros() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(aiTokenUsageRepository.sumTotalTokensByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(0L);
        when(automationRepository.countByOrganizationId(orgId)).thenReturn(0L);
        when(emailAccountRepository.countByOrganizationId(orgId)).thenReturn(0L);
        when(aiTokenUsageRepository.sumCostMicrosByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(0L);

        UsageResponse response = quotaService.getUsage(orgId);

        assertThat(response.usage().tokensUsedThisMonth()).isZero();
        assertThat(response.usage().activeAutomations()).isZero();
        assertThat(response.usage().emailAccounts()).isZero();
        assertThat(response.usage().costUsedCents()).isZero();
        assertThat(response.usage().costUsedMicros()).isZero();
    }

    @Test
    void getUsage_costMicrosRoundedDown_toCents() {
        when(planCacheService.loadPlanForOrg(orgId)).thenReturn(plan);
        when(aiTokenUsageRepository.sumTotalTokensByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(100L);
        when(automationRepository.countByOrganizationId(orgId)).thenReturn(1L);
        when(emailAccountRepository.countByOrganizationId(orgId)).thenReturn(1L);
        // 9999 micros = 0.9999 cents, should truncate to 0 cents
        when(aiTokenUsageRepository.sumCostMicrosByOrganizationSince(eq(orgId), any(Instant.class)))
                .thenReturn(9_999L);

        UsageResponse response = quotaService.getUsage(orgId);

        // Cents truncate to 0, but the raw micros are preserved so the UI can still show a
        // non-zero percentage for sub-cent usage (the "AI Limit stuck at 0%" fix).
        assertThat(response.usage().costUsedCents()).isZero();
        assertThat(response.usage().costUsedMicros()).isEqualTo(9_999L);
    }
}
