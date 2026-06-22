package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.dto.admin.*;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Plan;
import com.postwerk.model.User;
import com.postwerk.model.enums.ExecutionStatus;
import com.postwerk.model.enums.Role;
import com.postwerk.repository.*;
import com.postwerk.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AiTokenUsageRepository aiTokenUsageRepository;
    @Mock private AutomationExecutionRepository executionRepository;
    @Mock private AutomationRepository automationRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private PlanRepository planRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PlanCacheService planCacheService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private MembershipRepository membershipRepository;

    @InjectMocks
    private AdminServiceImpl adminService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        testUser = TestFixtures.createUser();
        testUser.setRole(Role.USER);
        userId = testUser.getId();
    }

    // ─── getStats ────────────────────────────────────────────────────

    @Test
    void getStats_returnsAggregatedCounts() {
        when(userRepository.countActive()).thenReturn(80L);
        when(userRepository.countDeleted()).thenReturn(20L);
        when(userRepository.countCreatedSince(any(Instant.class))).thenReturn(5L, 15L);
        when(aiTokenUsageRepository.sumPromptTokens()).thenReturn(1000L);
        when(aiTokenUsageRepository.sumOutputTokens()).thenReturn(2000L);
        when(executionRepository.count()).thenReturn(500L);
        when(executionRepository.countByStatus(ExecutionStatus.SUCCESS)).thenReturn(400L);
        when(executionRepository.countByStatus(ExecutionStatus.FAILED)).thenReturn(100L);
        when(automationRepository.countActive()).thenReturn(30L);
        when(emailRepository.count()).thenReturn(10000L);

        AdminStatsResponse stats = adminService.getStats();

        assertThat(stats.totalUsers()).isEqualTo(100L);
        assertThat(stats.activeUsers()).isEqualTo(80L);
        assertThat(stats.deletedUsers()).isEqualTo(20L);
        assertThat(stats.newUsersLast7Days()).isEqualTo(5L);
        assertThat(stats.newUsersLast30Days()).isEqualTo(15L);
        assertThat(stats.totalPromptTokens()).isEqualTo(1000L);
        assertThat(stats.totalOutputTokens()).isEqualTo(2000L);
        assertThat(stats.totalAutomationExecutions()).isEqualTo(500L);
        assertThat(stats.successfulExecutions()).isEqualTo(400L);
        assertThat(stats.failedExecutions()).isEqualTo(100L);
        assertThat(stats.activeAutomations()).isEqualTo(30L);
        assertThat(stats.totalEmails()).isEqualTo(10000L);
    }

    // ─── getUsers ────────────────────────────────────────────────────

    @Test
    void getUsers_searchByText_delegatesToRepo() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> page = new PageImpl<>(List.of(testUser));
        when(userRepository.searchUsers("test", "", pageable)).thenReturn(page);
        stubUserResponseDependencies();

        Page<AdminUserResponse> result = adminService.getUsers("test", null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).email()).isEqualTo(TestFixtures.TEST_EMAIL);
        verify(userRepository).searchUsers("test", "", pageable);
        verify(userRepository, never()).searchUsersByRole(anyString(), anyString(), anyString(), any());
        verify(userRepository, never()).searchDeletedUsers(anyString(), anyString(), any());
    }

    @Test
    void getUsers_filterByRole_delegatesToRoleQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> page = new PageImpl<>(List.of(testUser));
        when(userRepository.searchUsersByRole("", "ADMIN", "", pageable)).thenReturn(page);
        stubUserResponseDependencies();

        Page<AdminUserResponse> result = adminService.getUsers(null, "admin", null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).searchUsersByRole("", "ADMIN", "", pageable);
        verify(userRepository, never()).searchUsers(anyString(), anyString(), any());
    }

    @Test
    void getUsers_filterByPlan_passesPlanToQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> page = new PageImpl<>(List.of(testUser));
        when(userRepository.searchUsers("", "PRO", pageable)).thenReturn(page);
        stubUserResponseDependencies();

        Page<AdminUserResponse> result = adminService.getUsers(null, null, null, "PRO", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).searchUsers("", "PRO", pageable);
    }

    @Test
    void getUsers_filterByDeleted_delegatesToDeletedQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        testUser.setDeletedAt(Instant.now());
        Page<User> page = new PageImpl<>(List.of(testUser));
        when(userRepository.searchDeletedUsers("", "", pageable)).thenReturn(page);
        stubUserResponseDependencies();

        Page<AdminUserResponse> result = adminService.getUsers(null, null, "deleted", null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).deleted()).isTrue();
        verify(userRepository).searchDeletedUsers("", "", pageable);
        verify(userRepository, never()).searchUsers(anyString(), anyString(), any());
    }

    // ─── getUser ─────────────────────────────────────────────────────

    @Test
    void getUser_found_returnsResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        stubUserResponseDependencies();

        AdminUserResponse response = adminService.getUser(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo(TestFixtures.TEST_EMAIL);
        assertThat(response.fullName()).isEqualTo(TestFixtures.TEST_NAME);
        assertThat(response.company()).isEqualTo("Test Corp");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void getUser_notFound_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getUser(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    // ─── updateRole ──────────────────────────────────────────────────

    @Test
    void updateRole_validRole_updatesAndReturns() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);
        stubUserResponseDependencies();

        AdminUserResponse response = adminService.updateRole(userId, "ADMIN", "other@example.com");

        assertThat(testUser.getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.role()).isEqualTo("ADMIN");
        verify(userRepository).save(testUser);
    }

    @Test
    void updateRole_selfChange_throwsIllegalArgument() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> adminService.updateRole(userId, "ADMIN", TestFixtures.TEST_EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change your own role");
    }

    // ─── disableUser ─────────────────────────────────────────────────

    @Test
    void disableUser_setsDeletedAt() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);

        adminService.disableUser(userId);

        assertThat(testUser.getDeletedAt()).isNotNull();
        assertThat(testUser.getDeletionReason()).isEqualTo("ADMIN_DISABLED");
        verify(userRepository).save(testUser);
    }

    @Test
    void disableUser_notFound_throws() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.disableUser(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    // ─── getPlans ────────────────────────────────────────────────────

    @Test
    void getPlans_returnsMappedList() {
        Plan plan1 = createTestPlan("FREE", 10000, BigDecimal.ZERO);
        Plan plan2 = createTestPlan("PRO", 500000, new BigDecimal("29.00"));
        when(planRepository.findAll()).thenReturn(List.of(plan1, plan2));
        when(planRepository.countUsersByPlan(any(UUID.class))).thenReturn(5L, 3L);

        List<PlanResponse> plans = adminService.getPlans();

        assertThat(plans).hasSize(2);
        assertThat(plans.get(0).name()).isEqualTo("FREE");
        assertThat(plans.get(0).tokenLimit()).isEqualTo(10000);
        assertThat(plans.get(0).userCount()).isEqualTo(5L);
        assertThat(plans.get(1).name()).isEqualTo("PRO");
        assertThat(plans.get(1).price()).isEqualByComparingTo(new BigDecimal("29.00"));
        assertThat(plans.get(1).userCount()).isEqualTo(3L);
    }

    // ─── createPlan ──────────────────────────────────────────────────

    @Test
    void createPlan_savesAndReturns() {
        PlanRequest request = new PlanRequest("STARTER", 5000, 5, 3, new BigDecimal("9.99"), false, 500, 3, true);
        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> {
            Plan saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            return saved;
        });
        when(planRepository.countUsersByPlan(any(UUID.class))).thenReturn(0L);

        PlanResponse response = adminService.createPlan(request);

        assertThat(response.name()).isEqualTo("STARTER");
        assertThat(response.tokenLimit()).isEqualTo(5000);
        assertThat(response.automationLimit()).isEqualTo(5);
        assertThat(response.emailAccountLimit()).isEqualTo(3);
        assertThat(response.price()).isEqualByComparingTo(new BigDecimal("9.99"));
        verify(planRepository).save(any(Plan.class));
    }

    // ─── updatePlan ──────────────────────────────────────────────────

    @Test
    void updatePlan_found_updatesAllFields() {
        UUID planId = UUID.randomUUID();
        Plan existing = createTestPlan("OLD", 1000, BigDecimal.ZERO);
        existing.setId(planId);
        PlanRequest request = new PlanRequest("NEW", 50000, 25, 10, new BigDecimal("19.99"), false, 1000, 5, true);

        when(planRepository.findById(planId)).thenReturn(Optional.of(existing));
        when(planRepository.save(existing)).thenReturn(existing);
        when(planRepository.countUsersByPlan(planId)).thenReturn(2L);

        PlanResponse response = adminService.updatePlan(planId, request);

        assertThat(existing.getName()).isEqualTo("NEW");
        assertThat(existing.getTokenLimit()).isEqualTo(50000);
        assertThat(existing.getAutomationLimit()).isEqualTo(25);
        assertThat(existing.getEmailAccountLimit()).isEqualTo(10);
        assertThat(existing.getPrice()).isEqualByComparingTo(new BigDecimal("19.99"));
        assertThat(response.userCount()).isEqualTo(2L);
        verify(planRepository).save(existing);
    }

    @Test
    void updatePlan_notFound_throws() {
        UUID planId = UUID.randomUUID();
        PlanRequest request = new PlanRequest("X", 1, 1, 1, BigDecimal.ONE, false, 0, 1, true);
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updatePlan(planId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(planId.toString());
    }

    // ─── deletePlan ──────────────────────────────────────────────────

    @Test
    void deletePlan_found_deletes() {
        UUID planId = UUID.randomUUID();
        when(planRepository.existsById(planId)).thenReturn(true);

        adminService.deletePlan(planId);

        verify(planRepository).deleteById(planId);
    }

    @Test
    void deletePlan_notFound_throws() {
        UUID planId = UUID.randomUUID();
        when(planRepository.existsById(planId)).thenReturn(false);

        assertThatThrownBy(() -> adminService.deletePlan(planId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(planId.toString());

        verify(planRepository, never()).deleteById(any());
    }

    // ─── assignPlan ────────────────────────────────────────────────

    @Test
    void assignPlan_validIds_setsAndReturns() {
        UUID planId = UUID.randomUUID();
        Plan plan = createTestPlan("PRO", 500000, new BigDecimal("29.00"));
        plan.setId(planId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(userRepository.save(testUser)).thenReturn(testUser);
        stubUserResponseDependencies();

        AdminUserResponse response = adminService.assignPlan(userId, planId);

        assertThat(testUser.getPlan()).isEqualTo(plan);
        assertThat(response.id()).isEqualTo(userId);
        verify(userRepository).save(testUser);
    }

    @Test
    void assignPlan_userNotFound_throws() {
        UUID unknownUserId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(userRepository.findById(unknownUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.assignPlan(unknownUserId, planId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownUserId.toString());
    }

    @Test
    void assignPlan_planNotFound_throws() {
        UUID planId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.assignPlan(userId, planId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(planId.toString());
    }

    // ─── createPlan with costLimitCents ─────────────────────────

    @Test
    void createPlan_setCostLimitCents_persistsValue() {
        PlanRequest request = new PlanRequest("BUSINESS", 100000, 50, 20, new BigDecimal("49.00"), true, 2000, 10, true);
        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> {
            Plan saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            return saved;
        });
        when(planRepository.countUsersByPlan(any(UUID.class))).thenReturn(0L);

        PlanResponse response = adminService.createPlan(request);

        assertThat(response.costLimitCents()).isEqualTo(2000);
    }

    @Test
    void updatePlan_updatesCostLimitCents() {
        UUID planId = UUID.randomUUID();
        Plan existing = createTestPlan("PRO", 500000, new BigDecimal("29.00"));
        existing.setId(planId);
        existing.setCostLimitCents(500);

        PlanRequest request = new PlanRequest("PRO", 500000, 50, 20, new BigDecimal("29.00"), false, 1500, 3, true);
        when(planRepository.findById(planId)).thenReturn(Optional.of(existing));
        when(planRepository.save(existing)).thenReturn(existing);
        when(planRepository.countUsersByPlan(planId)).thenReturn(10L);

        PlanResponse response = adminService.updatePlan(planId, request);

        assertThat(existing.getCostLimitCents()).isEqualTo(1500);
        assertThat(response.costLimitCents()).isEqualTo(1500);
    }

    // ─── AI usage with cost ─────────────────────────────────────

    @Test
    void getAiUsageStats_includesTotalCostCents() {
        when(aiTokenUsageRepository.sumPromptTokens()).thenReturn(1000L);
        when(aiTokenUsageRepository.sumOutputTokens()).thenReturn(2000L);
        when(aiTokenUsageRepository.sumTotalTokens()).thenReturn(3000L);
        when(aiTokenUsageRepository.sumBillableChars()).thenReturn(5000L);
        when(aiTokenUsageRepository.sumCostMicros()).thenReturn(125_0000L); // 125 cents
        when(aiTokenUsageRepository.sumTokensGroupByModel()).thenReturn(List.of());
        when(aiTokenUsageRepository.sumTokensGroupByOperation()).thenReturn(List.of());

        AiUsageStatsResponse stats = adminService.getAiUsageStats();

        assertThat(stats.totalCostCents()).isEqualTo(125);
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private void stubUserResponseDependencies() {
        lenient().when(emailAccountRepository.countByUserId(any(UUID.class))).thenReturn(2L);
        lenient().when(automationRepository.countByUserId(any(UUID.class))).thenReturn(3L);
        lenient().when(aiTokenUsageRepository.sumTotalTokensByUser(any(UUID.class))).thenReturn(500L);
    }

    private Plan createTestPlan(String name, int tokenLimit, BigDecimal price) {
        return Plan.builder()
                .id(UUID.randomUUID())
                .name(name)
                .tokenLimit(tokenLimit)
                .automationLimit(10)
                .emailAccountLimit(5)
                .price(price)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
