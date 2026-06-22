package com.postwerk.service.impl;

import com.postwerk.dto.admin.*;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.AuditLog;
import com.postwerk.model.Automation;
import com.postwerk.model.AutomationExecution;
import com.postwerk.model.Membership;
import com.postwerk.model.Organization;
import com.postwerk.model.Plan;
import com.postwerk.model.StaffNote;
import com.postwerk.model.User;
import com.postwerk.model.enums.ExecutionStatus;
import com.postwerk.model.enums.Role;
import com.postwerk.model.enums.StaffRole;
import com.postwerk.repository.*;
import com.postwerk.service.AdminService;
import com.postwerk.service.AuthService;
import com.postwerk.service.PlanCacheService;
import com.postwerk.service.RefreshTokenService;
import com.postwerk.util.EnumUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.postwerk.util.MonetaryConstants.MICROS_PER_CENT;

/**
 * Default implementation of {@link AdminService}.
 * Provides platform-wide administration operations including user management,
 * AI usage analytics, automation monitoring, audit log access, and plan management.
 *
 * @since 1.0
 */
@Service
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final AiTokenUsageRepository aiTokenUsageRepository;
    private final AutomationExecutionRepository executionRepository;
    private final AutomationRepository automationRepository;
    private final AuditLogRepository auditLogRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmailRepository emailRepository;
    private final PlanRepository planRepository;
    private final RefreshTokenService refreshTokenService;
    private final PlanCacheService planCacheService;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final StaffNoteRepository staffNoteRepository;
    private final AuthService authService;

    public AdminServiceImpl(UserRepository userRepository,
                            AiTokenUsageRepository aiTokenUsageRepository,
                            AutomationExecutionRepository executionRepository,
                            AutomationRepository automationRepository,
                            AuditLogRepository auditLogRepository,
                            EmailAccountRepository emailAccountRepository,
                            EmailRepository emailRepository,
                            PlanRepository planRepository,
                            RefreshTokenService refreshTokenService,
                            PlanCacheService planCacheService,
                            OrganizationRepository organizationRepository,
                            MembershipRepository membershipRepository,
                            StaffNoteRepository staffNoteRepository,
                            AuthService authService) {
        this.userRepository = userRepository;
        this.aiTokenUsageRepository = aiTokenUsageRepository;
        this.executionRepository = executionRepository;
        this.automationRepository = automationRepository;
        this.auditLogRepository = auditLogRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.emailRepository = emailRepository;
        this.planRepository = planRepository;
        this.refreshTokenService = refreshTokenService;
        this.planCacheService = planCacheService;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.staffNoteRepository = staffNoteRepository;
        this.authService = authService;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long activeUsers = userRepository.countActive();
        long deletedUsers = userRepository.countDeleted();
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        return new AdminStatsResponse(
                activeUsers + deletedUsers,
                activeUsers,
                deletedUsers,
                userRepository.countCreatedSince(sevenDaysAgo),
                userRepository.countCreatedSince(thirtyDaysAgo),
                aiTokenUsageRepository.sumPromptTokens(),
                aiTokenUsageRepository.sumOutputTokens(),
                executionRepository.count(),
                executionRepository.countByStatus(ExecutionStatus.SUCCESS),
                executionRepository.countByStatus(ExecutionStatus.FAILED),
                automationRepository.countActive(),
                emailRepository.count()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(String search, String role, String status, String plan, Pageable pageable) {
        String s = search != null ? search : "";
        String p = plan != null ? plan.trim() : "";
        Page<User> page;

        if ("deleted".equalsIgnoreCase(status)) {
            page = userRepository.searchDeletedUsers(s, p, pageable);
        } else if (role != null && !role.isBlank()) {
            page = userRepository.searchUsersByRole(s, role.toUpperCase(), p, pageable);
        } else {
            page = userRepository.searchUsers(s, p, pageable);
        }

        // Batch the three per-user aggregates (account count, automation count, token sum) over the
        // page's user ids so we fire three GROUP BY queries instead of 3·N per-user queries (N+1 fix).
        UserAggregates aggregates = loadUserAggregates(
                page.getContent().stream().map(User::getId).toList());
        return page.map(user -> toAdminUserResponse(user, aggregates));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        return toAdminUserResponse(user, loadUserAggregates(List.of(userId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserOrgResponse> getUserOrganizations(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId.toString());
        }
        List<Membership> memberships = membershipRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        // Batch-load the orgs for name/slug/personal so we don't fire one query per membership (N+1 fix).
        List<UUID> orgIds = memberships.stream().map(Membership::getOrganizationId).distinct().toList();
        Map<UUID, Organization> orgsById = organizationRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(Organization::getId, o -> o));
        return memberships.stream()
                .map(m -> {
                    Organization org = orgsById.get(m.getOrganizationId());
                    if (org == null) return null; // soft-deleted org (filtered by @SQLRestriction)
                    return new AdminUserOrgResponse(
                            org.getId(), org.getName(), org.getSlug(), org.isPersonal(),
                            m.getRole().name(), m.getStatus().name(), m.getCreatedAt());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminMailboxResponse> getUserMailboxes(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId.toString());
        }
        // Ownership mirrors emailAccountCount: that count is emailAccountRepository.countByUserIdIn(..)
        // (GROUP BY EmailAccount.userId), so the list uses findByUserId(userId) to match it exactly.
        return emailAccountRepository.findByUserId(userId).stream()
                .map(AdminServiceImpl::toMailboxResponse)
                .toList();
    }

    /** Maps an {@link com.postwerk.model.EmailAccount} to its safe admin view (no passwords/secrets). */
    static AdminMailboxResponse toMailboxResponse(com.postwerk.model.EmailAccount account) {
        return new AdminMailboxResponse(
                account.getId(), account.getEmail(), account.getDisplayName(),
                account.getColor(), account.isActive(), account.getCreatedAt());
    }

    // ─── Users support tooling ───────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<StaffNoteResponse> listUserNotes(UUID targetUserId) {
        if (!userRepository.existsById(targetUserId)) {
            throw new ResourceNotFoundException("User", targetUserId.toString());
        }
        return staffNoteRepository.findByTargetUserIdOrderByCreatedAtDesc(targetUserId).stream()
                .map(AdminServiceImpl::toStaffNoteResponse)
                .toList();
    }

    @Override
    @Transactional
    public StaffNoteResponse addUserNote(UUID targetUserId, UUID authorUserId, String body) {
        if (!userRepository.existsById(targetUserId)) {
            throw new ResourceNotFoundException("User", targetUserId.toString());
        }
        // Snapshot the author's identity so the note keeps its attribution after the author is deleted
        // (author_user_id then nulls out via ON DELETE SET NULL).
        User author = userRepository.findById(authorUserId).orElse(null);
        StaffNote note = StaffNote.builder()
                .targetUserId(targetUserId)
                .authorUserId(authorUserId)
                .authorName(author != null ? author.getFullName() : null)
                .authorEmail(author != null ? author.getEmail() : null)
                .body(body)
                .build();
        staffNoteRepository.save(note);
        return toStaffNoteResponse(note);
    }

    @Override
    @Transactional
    public void deleteUserNote(UUID noteId, UUID requesterUserId, boolean requesterHasUserManage) {
        StaffNote note = staffNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("StaffNote", noteId.toString()));
        boolean isAuthor = requesterUserId != null && requesterUserId.equals(note.getAuthorUserId());
        if (!isAuthor && !requesterHasUserManage) {
            throw new AccessDeniedException("Only the note author or a user manager may delete this note");
        }
        staffNoteRepository.delete(note);
    }

    @Override
    @Transactional
    public void forcePasswordReset(UUID targetUserId, String ipAddress) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId.toString()));
        // Delegates to the normal self-service flow (audit + reset-email); never exposes/sets a password.
        authService.resetPasswordRequest(user.getEmail(), ipAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserSessionsResponse getUserSessions(UUID targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId.toString()));
        return new AdminUserSessionsResponse(refreshTokenService.countForUser(user.getEmail()));
    }

    @Override
    @Transactional
    public AdminUserSessionsResponse revokeUserSessions(UUID targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId.toString()));
        refreshTokenService.revokeAllForUser(user.getEmail());
        return new AdminUserSessionsResponse(0L);
    }

    /** Maps a {@link StaffNote} to its admin response view. */
    static StaffNoteResponse toStaffNoteResponse(StaffNote note) {
        return new StaffNoteResponse(
                note.getId(), note.getBody(), note.getAuthorName(),
                note.getAuthorEmail(), note.getCreatedAt());
    }

    @Override
    @Transactional
    public AdminUserResponse updateRole(UUID userId, String role, String currentUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        if (user.getEmail().equalsIgnoreCase(currentUserEmail)) {
            throw new IllegalArgumentException("Cannot change your own role");
        }
        user.setRole(Role.valueOf(role.toUpperCase()));
        userRepository.save(user);
        return toAdminUserResponse(user, loadUserAggregates(List.of(user.getId())));
    }

    @Override
    @Transactional
    public AdminUserResponse updateStaffRole(UUID userId, String staffRole, String currentUserEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        if (user.getEmail().equalsIgnoreCase(currentUserEmail)) {
            throw new IllegalArgumentException("Cannot change your own staff role");
        }
        // null/blank clears staff access; authorities are re-derived from the DB on the user's next
        // request, so granting/revoking takes effect immediately without forcing a re-login.
        if (staffRole == null || staffRole.isBlank()) {
            user.setStaffRole(null);
            user.setStaffRoleSince(null);
        } else {
            boolean firstGrant = user.getStaffRole() == null;
            user.setStaffRole(StaffRole.valueOf(staffRole.toUpperCase()));
            if (firstGrant) user.setStaffRoleSince(Instant.now());
        }
        userRepository.save(user);
        return toAdminUserResponse(user, loadUserAggregates(List.of(user.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public StaffIdentityResponse getStaffIdentity(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        StaffRole staffRole = user.getStaffRole();
        List<String> permissions = staffRole == null ? List.of()
                : staffRole.permissions().stream().map(Enum::name).sorted().toList();
        return new StaffIdentityResponse(
                user.getEmail(),
                user.getRole().name(),
                staffRole != null ? staffRole.name() : null,
                permissions);
    }

    @Override
    @Transactional
    public void disableUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        user.setDeletedAt(Instant.now());
        user.setDeletionReason("ADMIN_DISABLED");
        userRepository.save(user);
        // Revoke all active refresh tokens to prevent continued API access
        refreshTokenService.revokeAllForUser(user.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public AiUsageStatsResponse getAiUsageStats() {
        var byModel = aiTokenUsageRepository.sumTokensGroupByModel().stream()
                .map(r -> new AiUsageStatsResponse.ModelBreakdown(
                        (String) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue(), ((Number) r[3]).longValue()))
                .toList();

        var byOperation = aiTokenUsageRepository.sumTokensGroupByOperation().stream()
                .map(r -> new AiUsageStatsResponse.OperationBreakdown(
                        (String) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue(), ((Number) r[3]).longValue()))
                .toList();

        int totalCostCents = (int) (aiTokenUsageRepository.sumCostMicros() / MICROS_PER_CENT);

        return new AiUsageStatsResponse(
                aiTokenUsageRepository.sumPromptTokens(),
                aiTokenUsageRepository.sumOutputTokens(),
                aiTokenUsageRepository.sumTotalTokens(),
                aiTokenUsageRepository.sumBillableChars(),
                totalCostCents,
                byModel,
                byOperation
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiUsageByUserResponse> getAiUsageByUser() {
        var rows = aiTokenUsageRepository.sumTokensAndCostGroupByUser();
        List<UUID> userIds = rows.stream().map(r -> (UUID) r[0]).toList();
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return rows.stream()
                .map(r -> {
                    UUID uid = (UUID) r[0];
                    User user = userMap.get(uid);
                    int costCents = (int) (((Number) r[5]).longValue() / MICROS_PER_CENT);
                    return new AiUsageByUserResponse(
                            uid,
                            user != null ? user.getEmail() : "deleted",
                            user != null ? user.getFullName() : "Deleted User",
                            ((Number) r[1]).longValue(),
                            ((Number) r[2]).longValue(),
                            ((Number) r[3]).longValue(),
                            ((Number) r[4]).longValue(),
                            costCents
                    );
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimelineDataPoint> getAiUsageTimeline(String period) {
        Instant since;
        if ("monthly".equalsIgnoreCase(period)) {
            since = Instant.now().minus(365, ChronoUnit.DAYS);
        } else if ("weekly".equalsIgnoreCase(period)) {
            since = Instant.now().minus(90, ChronoUnit.DAYS);
        } else {
            since = Instant.now().minus(30, ChronoUnit.DAYS);
        }

        return aiTokenUsageRepository.dailyTokenUsage(since).stream()
                .map(r -> new TimelineDataPoint(r[0].toString(), ((Number) r[1]).longValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AutomationStatsResponse getAutomationStats() {
        long success = executionRepository.countByStatus(ExecutionStatus.SUCCESS);
        long failed = executionRepository.countByStatus(ExecutionStatus.FAILED);
        long running = executionRepository.countByStatus(ExecutionStatus.RUNNING);
        long total = success + failed + running;
        double successRate = total > 0 ? (double) success / total * 100 : 0;

        var topRows = executionRepository.topAutomationsByExecutionCount();
        List<UUID> topIds = topRows.stream().map(r -> (UUID) r[0]).toList();
        Map<UUID, String> automationNames = automationRepository.findAllById(topIds).stream()
                .collect(Collectors.toMap(Automation::getId, Automation::getName));

        var topAutomations = topRows.stream()
                .map(r -> {
                    UUID automationId = (UUID) r[0];
                    String name = automationNames.getOrDefault(automationId, "Deleted");
                    return new AutomationStatsResponse.TopAutomation(
                            automationId.toString(), name,
                            ((Number) r[1]).longValue(), ((Number) r[2]).longValue(), ((Number) r[3]).longValue());
                })
                .toList();

        return new AutomationStatsResponse(total, success, failed, running, successRate,
                automationRepository.countActive(), automationRepository.countAll(), topAutomations);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AutomationExecutionAdminResponse> getAutomationExecutions(Pageable pageable) {
        Page<AutomationExecution> page = executionRepository.findAllByOrderByTriggeredAtDesc(pageable);
        List<UUID> automationIds = page.getContent().stream()
                .map(AutomationExecution::getAutomationId).distinct().toList();
        Map<UUID, String> nameMap = automationRepository.findAllById(automationIds).stream()
                .collect(Collectors.toMap(Automation::getId, Automation::getName));

        return page.map(e -> {
            String name = nameMap.getOrDefault(e.getAutomationId(), "Deleted");
            return new AutomationExecutionAdminResponse(
                    e.getId(), e.getAutomationId(), name,
                    e.getStatus().name(), e.getProcessedCount(), e.getErrorLog(),
                    e.getTriggeredAt(), e.getCompletedAt());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponse> getAuditLog(UUID userId, String action, UUID organizationId, Pageable pageable) {
        AuditAction auditAction = (action != null && !action.isBlank())
                ? EnumUtil.parseOrThrow(AuditAction.class, action, "audit action")
                : null;
        Page<AuditLog> page = auditLogRepository.findForAdmin(userId, auditAction, organizationId, pageable);

        List<UUID> userIds = page.getContent().stream()
                .map(AuditLog::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return page.map(log -> {
            User user = log.getUserId() != null ? userMap.get(log.getUserId()) : null;
            return new AdminAuditLogResponse(
                    log.getId(), log.getUserId(),
                    user != null ? user.getEmail() : null,
                    user != null ? user.getFullName() : null,
                    log.getAction().name(), log.getDetail(), log.getIpAddress(), log.getCreatedAt()
            );
        });
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportAuditLogCsv(UUID userId, String action) {
        var logs = getAuditLog(userId, action, null, Pageable.unpaged());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8);
        writer.println("Date,User Email,User Name,Action,Detail,IP Address");

        for (var log : logs) {
            writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    log.createdAt(), nullSafe(log.userEmail()), nullSafe(log.userName()),
                    log.action(), nullSafe(log.detail()), nullSafe(log.ipAddress()));
        }

        writer.flush();
        return out.toByteArray();
    }

    // ─── Plan CRUD ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> getPlans() {
        return planRepository.findAll().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Override
    @Transactional
    public PlanResponse createPlan(PlanRequest request) {
        Plan plan = Plan.builder()
                .name(request.name())
                .tokenLimit(request.tokenLimit())
                .automationLimit(request.automationLimit())
                .emailAccountLimit(request.emailAccountLimit())
                .price(request.price())
                .apiWebhookEnabled(request.apiWebhookEnabled())
                .costLimitCents(request.costLimitCents())
                .inboundWebhookLimit(request.inboundWebhookLimit())
                .marketplacePublishEnabled(request.marketplacePublishEnabled() == null
                        ? true : request.marketplacePublishEnabled())
                .build();
        planRepository.save(plan);
        return toPlanResponse(plan);
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(UUID planId, PlanRequest request) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId.toString()));
        plan.setName(request.name());
        plan.setTokenLimit(request.tokenLimit());
        plan.setAutomationLimit(request.automationLimit());
        plan.setEmailAccountLimit(request.emailAccountLimit());
        plan.setPrice(request.price());
        plan.setApiWebhookEnabled(request.apiWebhookEnabled());
        plan.setCostLimitCents(request.costLimitCents());
        plan.setInboundWebhookLimit(request.inboundWebhookLimit());
        // Nullable: preserve the existing flag when the (older) plans editor omits it.
        if (request.marketplacePublishEnabled() != null) {
            plan.setMarketplacePublishEnabled(request.marketplacePublishEnabled());
        }
        planRepository.save(plan);
        planCacheService.evictAllUserPlans();
        return toPlanResponse(plan);
    }

    @Override
    @Transactional
    public void deletePlan(UUID planId) {
        if (!planRepository.existsById(planId)) {
            throw new ResourceNotFoundException("Plan", planId.toString());
        }
        planRepository.deleteById(planId);
        planCacheService.evictAllUserPlans();
    }

    @Override
    @Transactional
    public AdminUserResponse assignPlan(UUID userId, UUID planId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId.toString()));
        user.setPlan(plan);
        userRepository.save(user);
        planCacheService.evictUserPlan(userId);
        // Plans are billed per-organization (#4): keep the user's personal org in sync so the
        // assignment immediately drives quota enforcement for their default workspace.
        organizationRepository.findByOwnerUserIdAndPersonalTrue(userId).ifPresent(org -> {
            org.setPlan(plan);
            organizationRepository.save(org);
            planCacheService.evictOrgPlan(org.getId());
        });
        return toAdminUserResponse(user, loadUserAggregates(List.of(user.getId())));
    }

    private PlanResponse toPlanResponse(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getName(), plan.getTokenLimit(),
                plan.getAutomationLimit(), plan.getEmailAccountLimit(), plan.getPrice(),
                plan.isApiWebhookEnabled(), plan.getCostLimitCents(), plan.getInboundWebhookLimit(),
                plan.isMarketplacePublishEnabled(), Plan.DEFAULT_PLAN_NAME.equals(plan.getName()),
                planRepository.countUsersByPlan(plan.getId()), plan.getCreatedAt());
    }

    /**
     * Per-user aggregates (email accounts, automations, total AI tokens, active-org count, this-month AI
     * cost in micros, and the effective plan), keyed by user id. Built once per request via batched GROUP BY
     * queries so {@link #toAdminUserResponse} reads from memory instead of firing many queries per user
     * (N+1 fix). Users absent from a map default to 0; plans absent default to {@code null}.
     */
    private record UserAggregates(Map<UUID, Long> accountCounts,
                                  Map<UUID, Long> automationCounts,
                                  Map<UUID, Long> tokenSums,
                                  Map<UUID, Long> orgCounts,
                                  Map<UUID, Long> aiCostMicros,
                                  Map<UUID, Plan> plans) {
        long accounts(UUID userId) {
            return accountCounts.getOrDefault(userId, 0L);
        }

        long automations(UUID userId) {
            return automationCounts.getOrDefault(userId, 0L);
        }

        long tokens(UUID userId) {
            return tokenSums.getOrDefault(userId, 0L);
        }

        long orgs(UUID userId) {
            return orgCounts.getOrDefault(userId, 0L);
        }

        long cost(UUID userId) {
            return aiCostMicros.getOrDefault(userId, 0L);
        }

        Plan plan(UUID userId) {
            return plans.get(userId);
        }
    }

    /** Loads all per-user aggregates for the given user ids in batched queries (N+1-safe). */
    private UserAggregates loadUserAggregates(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return new UserAggregates(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        // A user's effective plan = their personal org's plan (same canonical source as quota enforcement;
        // assignPlan keeps the personal org in sync). Batch-load personal orgs (plan eager-fetched) keyed by owner.
        Map<UUID, Plan> plansByUser = new HashMap<>();
        for (Organization org : organizationRepository.findPersonalByOwnerUserIds(userIds)) {
            if (org.getOwnerUserId() != null) {
                plansByUser.put(org.getOwnerUserId(), org.getPlan());
            }
        }
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new UserAggregates(
                toCountMap(emailAccountRepository.countByUserIdIn(userIds)),
                toCountMap(automationRepository.countByUserIdIn(userIds)),
                toCountMap(aiTokenUsageRepository.sumTotalTokensByUserIn(userIds)),
                toCountMap(membershipRepository.countActiveMembershipsByUserIds(userIds)),
                toCountMap(aiTokenUsageRepository.sumCostMicrosByUserInSince(userIds, monthStart)),
                plansByUser);
    }

    /** Collapses {@code [userId, count]} aggregate rows into a {@code Map<UUID, Long>}. */
    private Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    private AdminUserResponse toAdminUserResponse(User user, UserAggregates aggregates) {
        Plan plan = aggregates.plan(user.getId());
        return new AdminUserResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getCompany(),
                user.getRole().name(),
                user.getStaffRole() != null ? user.getStaffRole().name() : null,
                user.getLastLoginAt(), user.getLastLoginIp(),
                user.getCreatedAt(), user.getDeletedAt() != null,
                aggregates.accounts(user.getId()),
                aggregates.automations(user.getId()),
                aggregates.tokens(user.getId()),
                plan != null ? plan.getName() : null,
                aggregates.orgs(user.getId()),
                aggregates.cost(user.getId()),
                plan != null ? plan.getCostLimitCents() : 0L
        );
    }

    private String nullSafe(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "\"\"");
        // Prevent CSV formula injection in spreadsheet applications
        if (!escaped.isEmpty() && "=+-@".indexOf(escaped.charAt(0)) >= 0) {
            escaped = "'" + escaped;
        }
        return escaped;
    }
}
