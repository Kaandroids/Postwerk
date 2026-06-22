package com.postwerk.service.impl;

import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Organization;
import com.postwerk.model.Plan;
import com.postwerk.repository.AiTokenUsageRepository;
import com.postwerk.repository.AuditLogRepository;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.MembershipRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.PlanRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.PlanCacheService;
import com.postwerk.service.QuotaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminSubscriptionServiceImpl} — the financial/aggregation logic of the admin
 * Subscriptions screen: MRR (sum of active plan prices), active-subscription count, AI-cost rollup,
 * over-cap detection (&gt;90% of the effective cap), and the in-memory status filter. Payment is
 * metadata-only, so MRR is derived, never charged.
 */
@ExtendWith(MockitoExtension.class)
class AdminSubscriptionServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private AutomationRepository automationRepository;
    @Mock private AiTokenUsageRepository aiTokenUsageRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private QuotaService quotaService;
    @Mock private AuditService auditService;
    @Mock private PlanCacheService planCacheService;

    @InjectMocks
    private AdminSubscriptionServiceImpl service;

    private Plan plan(String name, String price, int capCents) {
        return Plan.builder().id(UUID.randomUUID()).name(name)
                .price(price == null ? null : new BigDecimal(price)).costLimitCents(capCents).build();
    }

    private Organization org(String name, Plan plan, boolean suspended) {
        return Organization.builder().id(UUID.randomUUID()).name(name).slug(name.toLowerCase())
                .plan(plan).personal(false).ownerUserId(null)
                .suspendedAt(suspended ? Instant.now() : null).createdAt(Instant.now()).build();
    }

    /** Stubs the batched count/cost queries used by loadRows() to "no rows" (0 each). */
    private void stubEmptyCounts() {
        when(membershipRepository.countMembersByOrgIds(anyList())).thenReturn(List.of());
        when(emailAccountRepository.countByOrganizationIdIn(anyList())).thenReturn(List.of());
        when(automationRepository.countByOrganizationIdIn(anyList())).thenReturn(List.of());
        when(aiTokenUsageRepository.sumCostMicrosByOrganizationInSince(anyList(), any())).thenReturn(List.of());
        when(quotaService.effectiveCapCents(any(), anyLong())).thenReturn(500L);
    }

    @Test
    void kpis_sumsMrrForActiveOrgsOnly_andCountsActive() {
        var org1 = org("Acme", plan("PRO", "5.00", 500), false);
        var org2 = org("Beta", plan("ENT", "9.99", -1), true);  // suspended → excluded from MRR/active
        var org3 = org("Gamma", null, false);                   // no plan → not counted in MRR
        when(organizationRepository.findAllWithPlanForAdmin()).thenReturn(List.of(org1, org2, org3));
        stubEmptyCounts();
        when(planRepository.count()).thenReturn(3L);

        var kpis = service.kpis();

        assertThat(kpis.mrr()).isEqualByComparingTo("5.00");
        assertThat(kpis.activeSubscriptions()).isEqualTo(2); // org1 + org3
        assertThat(kpis.aiCostCentsThisMonth()).isZero();
        assertThat(kpis.overCapCount()).isZero();
        assertThat(kpis.planCount()).isEqualTo(3);
    }

    @Test
    void kpis_flagsOrgsOverNinetyPercentOfCap() {
        var org1 = org("Acme", plan("PRO", "5.00", 500), false);
        when(organizationRepository.findAllWithPlanForAdmin()).thenReturn(List.of(org1));
        when(membershipRepository.countMembersByOrgIds(anyList())).thenReturn(List.of());
        when(emailAccountRepository.countByOrganizationIdIn(anyList())).thenReturn(List.of());
        when(automationRepository.countByOrganizationIdIn(anyList())).thenReturn(List.of());
        // 5,000,000 micros = 500 cents, which is > 90% of the 500-cent cap → over cap.
        when(aiTokenUsageRepository.sumCostMicrosByOrganizationInSince(anyList(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{org1.getId(), 5_000_000L}));
        when(quotaService.effectiveCapCents(any(), anyLong())).thenReturn(500L);
        when(planRepository.count()).thenReturn(1L);

        var kpis = service.kpis();

        assertThat(kpis.overCapCount()).isEqualTo(1);
        assertThat(kpis.aiCostCentsThisMonth()).isEqualTo(500);
    }

    @Test
    void listSubscriptions_statusFilter_returnsOnlySuspended() {
        var active = org("Acme", plan("PRO", "5.00", 500), false);
        var suspended = org("Beta", plan("PRO", "5.00", 500), true);
        when(organizationRepository.findAllWithPlanForAdmin()).thenReturn(List.of(active, suspended));
        stubEmptyCounts();

        var page = service.listSubscriptions(null, null, "suspended", null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).orgName()).isEqualTo("Beta");
        assertThat(page.getContent().get(0).status()).isEqualTo("suspended");
    }

    @Test
    void getSubscription_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(organizationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubscription(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
