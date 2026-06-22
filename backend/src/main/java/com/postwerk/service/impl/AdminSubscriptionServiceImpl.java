package com.postwerk.service.impl;

import com.postwerk.dto.admin.AdminSubscriptionDetailResponse;
import com.postwerk.dto.admin.AdminSubscriptionResponse;
import com.postwerk.dto.admin.PlanHistoryEntryResponse;
import com.postwerk.dto.admin.SubscriptionKpisResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.AuditLog;
import com.postwerk.model.Organization;
import com.postwerk.model.Plan;
import com.postwerk.model.User;
import com.postwerk.repository.AiTokenUsageRepository;
import com.postwerk.repository.AuditLogRepository;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.MembershipRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.PlanRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AdminSubscriptionService;
import com.postwerk.service.AuditService;
import com.postwerk.service.PlanCacheService;
import com.postwerk.service.QuotaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdminSubscriptionService}. Builds the subscription rows in-memory over all orgs with
 * batched count/cost queries (N+1-safe), resolving each org's effective AI cap via {@link QuotaService}.
 * MRR is derived (plan price × active subs) — payment is metadata-only.
 *
 * @since 1.0
 */
@Service
public class AdminSubscriptionServiceImpl implements AdminSubscriptionService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final MembershipRepository membershipRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final AutomationRepository automationRepository;
    private final AiTokenUsageRepository aiTokenUsageRepository;
    private final AuditLogRepository auditLogRepository;
    private final QuotaService quotaService;
    private final AuditService auditService;
    private final PlanCacheService planCacheService;

    public AdminSubscriptionServiceImpl(OrganizationRepository organizationRepository,
                                        UserRepository userRepository,
                                        PlanRepository planRepository,
                                        MembershipRepository membershipRepository,
                                        EmailAccountRepository emailAccountRepository,
                                        AutomationRepository automationRepository,
                                        AiTokenUsageRepository aiTokenUsageRepository,
                                        AuditLogRepository auditLogRepository,
                                        QuotaService quotaService,
                                        AuditService auditService,
                                        PlanCacheService planCacheService) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.membershipRepository = membershipRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.automationRepository = automationRepository;
        this.aiTokenUsageRepository = aiTokenUsageRepository;
        this.auditLogRepository = auditLogRepository;
        this.quotaService = quotaService;
        this.auditService = auditService;
        this.planCacheService = planCacheService;
    }

    /** An org + its resolved owner/usage/effective-cap, used to build the response rows. */
    private record Row(Organization org, String ownerName, String ownerEmail,
                       long members, long mailboxes, long automations, long aiCostMicros, long effectiveCapCents) {
        boolean suspended() { return org.getSuspendedAt() != null; }
        long aiCostCents() { return aiCostMicros / 10_000; }
    }

    private static Instant monthStart() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    // ── List ────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<AdminSubscriptionResponse> listSubscriptions(String search, String plan, String status,
                                                             String usage, Pageable pageable) {
        String q = search == null ? "" : search.trim().toLowerCase();
        List<Row> rows = loadRows().stream().filter(r -> {
            if (!q.isEmpty() && !(contains(r.org.getName(), q) || contains(r.org.getSlug(), q)
                    || contains(r.ownerName, q) || contains(r.ownerEmail, q) || contains(planName(r), q))) {
                return false;
            }
            if (plan != null && !plan.isBlank() && !plan.equals(planName(r))) return false;
            if (status != null && !status.isBlank()) {
                if ("active".equals(status) && r.suspended()) return false;
                if ("suspended".equals(status) && !r.suspended()) return false;
            }
            if (usage != null && !usage.isBlank()) {
                long cap = r.effectiveCapCents();
                if ("unlimited".equals(usage) && cap != -1) return false;
                if ("aiOff".equals(usage) && cap != 0) return false;
                if ("over90".equals(usage) && !(cap > 0 && r.aiCostCents() > cap * 0.9)) return false;
            }
            return true;
        }).sorted(Comparator.comparing(r -> r.org.getName() == null ? "" : r.org.getName().toLowerCase()))
                .collect(Collectors.toList());

        int total = rows.size();
        int start = (int) pageable.getOffset();
        if (start >= total) return new PageImpl<>(List.of(), pageable, total);
        int end = Math.min(start + pageable.getPageSize(), total);
        List<AdminSubscriptionResponse> content = rows.subList(start, end).stream()
                .map(this::toRowResponse).toList();
        return new PageImpl<>(content, pageable, total);
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public SubscriptionKpisResponse kpis() {
        List<Row> rows = loadRows();
        BigDecimal mrr = BigDecimal.ZERO;
        long active = 0, overCap = 0, aiCostCents = 0;
        for (Row r : rows) {
            aiCostCents += r.aiCostCents();
            if (!r.suspended()) {
                active++;
                if (r.org.getPlan() != null && r.org.getPlan().getPrice() != null) {
                    mrr = mrr.add(r.org.getPlan().getPrice());
                }
                if (r.effectiveCapCents() > 0 && r.aiCostCents() > r.effectiveCapCents() * 0.9) overCap++;
            }
        }
        return new SubscriptionKpisResponse(mrr, active, aiCostCents, overCap, planRepository.count());
    }

    // ── Detail ────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public AdminSubscriptionDetailResponse getSubscription(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId.toString()));
        Plan plan = org.getPlan();
        User owner = org.getOwnerUserId() == null ? null
                : userRepository.findById(org.getOwnerUserId()).orElse(null);
        long members = firstCount(membershipRepository.countMembersByOrgIds(List.of(orgId)));
        long mailboxes = emailAccountRepository.countByOrganizationId(orgId);
        long automations = automationRepository.countByOrganizationId(orgId);
        long aiCost = aiTokenUsageRepository.sumCostMicrosByOrganizationSince(orgId, monthStart());
        int baseCap = plan != null ? plan.getCostLimitCents() : 0;
        long effective = quotaService.effectiveCapCents(orgId, baseCap);

        return new AdminSubscriptionDetailResponse(
                org.getId(), org.getName(), org.getSlug(), org.isPersonal(),
                owner != null ? owner.getFullName() : null,
                owner != null ? owner.getEmail() : null,
                org.getSuspendedAt() != null ? "suspended" : "active",
                plan != null ? plan.getId() : null,
                plan != null ? plan.getName() : null,
                plan != null ? plan.getPrice() : null,
                baseCap, effective, aiCost,
                members, mailboxes, automations,
                plan != null ? plan.getEmailAccountLimit() : 0,
                plan != null ? plan.getAutomationLimit() : 0,
                org.getCreatedAt(), org.getSuspendedAt(),
                planHistory(orgId));
    }

    // ── Change plan ─────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AdminSubscriptionDetailResponse changePlan(UUID orgId, UUID planId, String reason, UUID actorUserId, String ip) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId.toString()));
        Plan newPlan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId.toString()));
        String oldName = org.getPlan() != null ? org.getPlan().getName() : "—";
        org.setPlan(newPlan);
        organizationRepository.save(org);
        planCacheService.evictOrgPlan(orgId);

        String detail = oldName + " → " + newPlan.getName()
                + (reason != null && !reason.isBlank() ? " · " + reason.trim() : "");
        auditService.log(actorUserId, AuditAction.ORG_PLAN_CHANGED, detail, ip);
        return getSubscription(orgId);
    }

    // ── Plan history ──────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<PlanHistoryEntryResponse> planHistory(UUID orgId) {
        List<AuditLog> logs = auditLogRepository
                .findByOrganizationIdAndActionOrderByCreatedAtDesc(orgId, AuditAction.ORG_PLAN_CHANGED, PageRequest.of(0, 20))
                .getContent();
        if (logs.isEmpty()) return List.of();
        Set<UUID> actorIds = logs.stream().map(AuditLog::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> actorNames = new HashMap<>();
        if (!actorIds.isEmpty()) {
            userRepository.findAllById(actorIds).forEach(u ->
                    actorNames.put(u.getId(), u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail()));
        }
        return logs.stream()
                .map(l -> new PlanHistoryEntryResponse(l.getCreatedAt(), l.getDetail(),
                        actorNames.getOrDefault(l.getUserId(), null)))
                .toList();
    }

    // ── Loading ────────────────────────────────────────────────────────────────
    private List<Row> loadRows() {
        List<Organization> orgs = organizationRepository.findAllWithPlanForAdmin();
        if (orgs.isEmpty()) return List.of();

        List<UUID> orgIds = orgs.stream().map(Organization::getId).toList();
        Instant since = monthStart();

        Map<UUID, User> owners = new HashMap<>();
        Set<UUID> ownerIds = orgs.stream().map(Organization::getOwnerUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!ownerIds.isEmpty()) userRepository.findAllById(ownerIds).forEach(u -> owners.put(u.getId(), u));

        Map<UUID, Long> memberCounts = pairMap(membershipRepository.countMembersByOrgIds(orgIds));
        Map<UUID, Long> mailboxCounts = pairMap(emailAccountRepository.countByOrganizationIdIn(orgIds));
        Map<UUID, Long> automationCounts = pairMap(automationRepository.countByOrganizationIdIn(orgIds));
        Map<UUID, Long> aiCosts = pairMap(aiTokenUsageRepository.sumCostMicrosByOrganizationInSince(orgIds, since));

        return orgs.stream().map(o -> {
            User owner = owners.get(o.getOwnerUserId());
            int baseCap = o.getPlan() != null ? o.getPlan().getCostLimitCents() : 0;
            long effective = quotaService.effectiveCapCents(o.getId(), baseCap);
            return new Row(o,
                    owner != null ? owner.getFullName() : null,
                    owner != null ? owner.getEmail() : null,
                    memberCounts.getOrDefault(o.getId(), 0L),
                    mailboxCounts.getOrDefault(o.getId(), 0L),
                    automationCounts.getOrDefault(o.getId(), 0L),
                    aiCosts.getOrDefault(o.getId(), 0L),
                    effective);
        }).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private AdminSubscriptionResponse toRowResponse(Row r) {
        Organization o = r.org;
        return new AdminSubscriptionResponse(
                o.getId(), o.getName(), o.getSlug(), o.isPersonal(),
                r.ownerName, r.ownerEmail, planName(r),
                r.suspended() ? "suspended" : "active",
                r.members, r.mailboxes, r.automations, r.aiCostMicros, r.effectiveCapCents,
                o.getCreatedAt());
    }

    private static String planName(Row r) {
        return r.org.getPlan() != null ? r.org.getPlan().getName() : null;
    }

    private static Map<UUID, Long> pairMap(List<Object[]> rows) {
        Map<UUID, Long> m = new HashMap<>();
        for (Object[] row : rows) m.put((UUID) row[0], ((Number) row[1]).longValue());
        return m;
    }

    private static long firstCount(List<Object[]> rows) {
        return rows.isEmpty() ? 0 : ((Number) rows.get(0)[1]).longValue();
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase().contains(lowerNeedle);
    }
}
