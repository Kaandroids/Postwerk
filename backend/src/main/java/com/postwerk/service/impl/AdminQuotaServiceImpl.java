package com.postwerk.service.impl;

import com.postwerk.dto.admin.QuotaKpisResponse;
import com.postwerk.dto.admin.QuotaOverrideRequest;
import com.postwerk.dto.admin.QuotaOverrideResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.Organization;
import com.postwerk.model.Plan;
import com.postwerk.model.QuotaOverride;
import com.postwerk.model.User;
import com.postwerk.model.enums.QuotaOverrideKind;
import com.postwerk.model.enums.QuotaOverrideTargetType;
import com.postwerk.repository.AiTokenUsageRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.QuotaOverrideRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AdminQuotaService;
import com.postwerk.service.AuditService;
import com.postwerk.service.QuotaService;
import com.postwerk.util.EnumUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.postwerk.util.MonetaryConstants.MICROS_PER_CENT;

/**
 * Default implementation of {@link AdminQuotaService}. Enforcement-org resolution and the effective
 * cap both reuse {@link QuotaService} so the admin list mirrors what enforcement applies. The list
 * path batch-loads orgs, plans, users and per-org spend so a page of N rows fires a fixed number of
 * queries (N+1-safe); text search runs in Java over the page rows so it can match the resolved
 * target name/email/slug.
 *
 * @since 1.0
 */
@Service
public class AdminQuotaServiceImpl implements AdminQuotaService {

    private final QuotaOverrideRepository quotaOverrideRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AiTokenUsageRepository aiTokenUsageRepository;
    private final QuotaService quotaService;
    private final AuditService auditService;

    public AdminQuotaServiceImpl(QuotaOverrideRepository quotaOverrideRepository,
                                 OrganizationRepository organizationRepository,
                                 UserRepository userRepository,
                                 AiTokenUsageRepository aiTokenUsageRepository,
                                 QuotaService quotaService,
                                 AuditService auditService) {
        this.quotaOverrideRepository = quotaOverrideRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.aiTokenUsageRepository = aiTokenUsageRepository;
        this.quotaService = quotaService;
        this.auditService = auditService;
    }

    // ─── List ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<QuotaOverrideResponse> list(String search, String targetType, String kind,
                                            String status, String expiry, Pageable pageable) {
        Instant now = Instant.now();
        // Validate then pass the enum NAME (or "" sentinel) — null params can't be type-inferred by Postgres.
        String typeFilter = targetType == null || targetType.isBlank() ? ""
                : EnumUtil.parseOrThrow(QuotaOverrideTargetType.class, targetType, "target type").name();
        String kindFilter = kind == null || kind.isBlank() ? ""
                : EnumUtil.parseOrThrow(QuotaOverrideKind.class, kind, "override kind").name();
        int statusMode = 0; // 0 = both, 1 = active, 2 = expired
        if (status != null && !status.isBlank()) {
            if ("active".equalsIgnoreCase(status)) statusMode = 1;
            else if ("expired".equalsIgnoreCase(status)) statusMode = 2;
        }
        boolean expiryFilter = false;
        Instant expiryWindowEnd = now; // non-null sentinel; only consulted when expiryFilter is true
        if (expiry != null && !expiry.isBlank()) {
            if ("next7".equalsIgnoreCase(expiry)) { expiryFilter = true; expiryWindowEnd = now.plus(7, ChronoUnit.DAYS); }
            else if ("next30".equalsIgnoreCase(expiry)) { expiryFilter = true; expiryWindowEnd = now.plus(30, ChronoUnit.DAYS); }
        }

        Page<QuotaOverride> page = quotaOverrideRepository.search(
                typeFilter, kindFilter, statusMode, expiryFilter, expiryWindowEnd, now, pageable);

        // Batch-load everything the page rows need, then build responses + apply the text filter in Java.
        Context ctx = loadContext(page.getContent(), now);
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        List<QuotaOverrideResponse> rows = page.getContent().stream()
                .map(o -> toResponse(o, ctx, now))
                .filter(r -> q.isEmpty() || matchesSearch(r, q))
                .toList();

        // When a search term is present we filter in-page, so report the post-filter size for that page
        // (the underlying paged total still reflects the DB-level filters, which is the dominant case).
        if (!q.isEmpty()) {
            return new PageImpl<>(rows, pageable, rows.size());
        }
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    private boolean matchesSearch(QuotaOverrideResponse r, String q) {
        return (r.targetName() != null && r.targetName().toLowerCase(Locale.ROOT).contains(q))
                || (r.targetEmailOrSlug() != null && r.targetEmailOrSlug().toLowerCase(Locale.ROOT).contains(q))
                || r.id().toString().toLowerCase(Locale.ROOT).contains(q);
    }

    // ─── Create / Update / Revoke ────────────────────────────────────

    @Override
    @Transactional
    public QuotaOverrideResponse create(QuotaOverrideRequest request, UUID staffUserId, String ipAddress) {
        QuotaOverrideTargetType targetType =
                EnumUtil.parseOrThrow(QuotaOverrideTargetType.class, request.targetType(), "target type");
        QuotaOverrideKind kind = EnumUtil.parseOrThrow(QuotaOverrideKind.class, request.kind(), "override kind");
        Long amount = validateAmount(kind, request.amountCents());

        UUID orgId = resolveEnforcementOrg(targetType, request.targetId());

        User staff = staffUserId == null ? null : userRepository.findById(staffUserId).orElse(null);
        QuotaOverride override = QuotaOverride.builder()
                .targetType(targetType)
                .targetId(request.targetId())
                .organizationId(orgId)
                .kind(kind)
                .amountCents(amount)
                .expiresAt(request.expiresAt())
                .reason(request.reason().trim())
                .grantedByUserId(staffUserId)
                .grantedByName(staff != null ? staff.getFullName() : null)
                .build();
        quotaOverrideRepository.save(override);

        auditService.log(staffUserId, AuditAction.QUOTA_OVERRIDE_GRANTED,
                "Quota override " + override.getId() + " (" + kind + ") for " + targetType
                        + " " + request.targetId() + (amount != null ? " amount=" + amount + "c" : ""),
                ipAddress);

        return toResponse(override, loadContext(List.of(override), Instant.now()), Instant.now());
    }

    @Override
    @Transactional
    public QuotaOverrideResponse update(UUID id, QuotaOverrideRequest request, UUID staffUserId, String ipAddress) {
        QuotaOverride override = quotaOverrideRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuotaOverride", id.toString()));

        QuotaOverrideKind kind = EnumUtil.parseOrThrow(QuotaOverrideKind.class, request.kind(), "override kind");
        Long amount = validateAmount(kind, request.amountCents());

        // Target is LOCKED: the request's targetType/targetId are ignored; the stored target/org stay.
        override.setKind(kind);
        override.setAmountCents(amount);
        override.setExpiresAt(request.expiresAt());
        override.setReason(request.reason().trim());
        quotaOverrideRepository.save(override);

        auditService.log(staffUserId, AuditAction.QUOTA_OVERRIDE_UPDATED,
                "Quota override " + override.getId() + " set to " + kind
                        + (amount != null ? " amount=" + amount + "c" : ""),
                ipAddress);

        return toResponse(override, loadContext(List.of(override), Instant.now()), Instant.now());
    }

    @Override
    @Transactional
    public void revoke(UUID id, UUID staffUserId, String ipAddress) {
        QuotaOverride override = quotaOverrideRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuotaOverride", id.toString()));
        quotaOverrideRepository.delete(override);
        auditService.log(staffUserId, AuditAction.QUOTA_OVERRIDE_REVOKED,
                "Quota override " + id + " (" + override.getKind() + ") revoked for "
                        + override.getTargetType() + " " + override.getTargetId(),
                ipAddress);
    }

    // ─── KPIs ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public QuotaKpisResponse kpis() {
        Instant now = Instant.now();
        long activeCount = quotaOverrideRepository.countActive(now);
        long expiringIn7 = quotaOverrideRepository.countExpiringBetween(now, now.plus(7, ChronoUnit.DAYS));

        List<QuotaOverride> active = quotaOverrideRepository.findAllActive(now);

        // Credit granted this calendar month (active CREDITs created since the 1st, UTC).
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long creditThisMonth = active.stream()
                .filter(o -> o.getKind() == QuotaOverrideKind.CREDIT)
                .filter(o -> o.getAmountCents() != null)
                .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(monthStart))
                .mapToLong(QuotaOverride::getAmountCents)
                .sum();

        // Over 80%: non-unlimited active overrides whose org spends > 80% of its effective cap.
        long over80 = countOver80(active, monthStart);

        return new QuotaKpisResponse(activeCount, creditThisMonth, over80, expiringIn7);
    }

    private long countOver80(List<QuotaOverride> active, Instant monthStart) {
        Set<UUID> orgIds = active.stream().map(QuotaOverride::getOrganizationId).collect(Collectors.toSet());
        if (orgIds.isEmpty()) return 0L;
        Map<UUID, Plan> plansByOrg = loadPlansByOrg(orgIds);
        Map<UUID, Long> spendByOrg = loadSpendByOrg(orgIds, monthStart);

        long count = 0;
        for (QuotaOverride o : active) {
            if (o.getKind() == QuotaOverrideKind.UNLIMITED) continue;
            long baseCap = baseCapOf(plansByOrg.get(o.getOrganizationId()));
            long effective = quotaService.effectiveCapCents(o.getOrganizationId(), baseCap);
            if (effective <= 0) continue; // unlimited (-1) or disabled (0) → no meaningful ratio
            long spendCents = spendByOrg.getOrDefault(o.getOrganizationId(), 0L) / MICROS_PER_CENT;
            if ((double) spendCents / effective > 0.8) count++;
        }
        return count;
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /** CREDIT/CAP require a positive amount; UNLIMITED ignores it (stored null). 400 on violation. */
    private Long validateAmount(QuotaOverrideKind kind, Long amountCents) {
        if (kind == QuotaOverrideKind.UNLIMITED) {
            return null;
        }
        if (amountCents == null || amountCents <= 0) {
            throw new IllegalArgumentException(kind + " override requires a positive amountCents");
        }
        return amountCents;
    }

    /** Resolves the enforcement org: ORG → itself (404 if missing); USER → its personal org (404 if missing). */
    private UUID resolveEnforcementOrg(QuotaOverrideTargetType targetType, UUID targetId) {
        if (targetType == QuotaOverrideTargetType.ORG) {
            Organization org = organizationRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", targetId.toString()));
            return org.getId();
        }
        Organization personal = organizationRepository.findByOwnerUserIdAndPersonalTrue(targetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Personal organization for user", targetId.toString()));
        return personal.getId();
    }

    private long baseCapOf(Plan plan) {
        return plan != null ? plan.getCostLimitCents() : 0L;
    }

    /**
     * Batch-loaded lookup tables for a set of override rows: enforcement orgs (plan eager-fetched),
     * target users (for USER rows), this-month spend per org, and the cached active-override sets used
     * by {@code effectiveCap}. Built once per request so {@link #toResponse} reads from memory (N+1-safe).
     */
    private record Context(Map<UUID, Organization> orgsById,
                           Map<UUID, User> usersById,
                           Map<UUID, Long> spendMicrosByOrg) {
    }

    private Context loadContext(List<QuotaOverride> overrides, Instant now) {
        if (overrides.isEmpty()) {
            return new Context(Map.of(), Map.of(), Map.of());
        }
        Set<UUID> orgIds = overrides.stream().map(QuotaOverride::getOrganizationId).collect(Collectors.toSet());
        Set<UUID> userIds = overrides.stream()
                .filter(o -> o.getTargetType() == QuotaOverrideTargetType.USER)
                .map(QuotaOverride::getTargetId)
                .collect(Collectors.toSet());

        Map<UUID, Organization> orgsById = new HashMap<>();
        organizationRepository.findAllById(orgIds).forEach(o -> orgsById.put(o.getId(), o));

        Map<UUID, User> usersById = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> usersById.put(u.getId(), u));
        }

        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<UUID, Long> spendByOrg = loadSpendByOrg(orgIds, monthStart);

        return new Context(orgsById, usersById, spendByOrg);
    }

    private Map<UUID, Plan> loadPlansByOrg(Set<UUID> orgIds) {
        Map<UUID, Plan> map = new HashMap<>();
        organizationRepository.findAllById(orgIds).forEach(o -> map.put(o.getId(), o.getPlan()));
        return map;
    }

    private Map<UUID, Long> loadSpendByOrg(Set<UUID> orgIds, Instant since) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : aiTokenUsageRepository.sumCostMicrosByOrganizationInSince(new ArrayList<>(orgIds), since)) {
            map.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    private QuotaOverrideResponse toResponse(QuotaOverride o, Context ctx, Instant now) {
        Organization org = ctx.orgsById().get(o.getOrganizationId());
        Plan plan = org != null ? org.getPlan() : null;
        long baseCap = baseCapOf(plan);
        long effective = quotaService.effectiveCapCents(o.getOrganizationId(), baseCap);
        Long effectiveBoxed = effective == -1 ? null : effective;
        long spendCents = ctx.spendMicrosByOrg().getOrDefault(o.getOrganizationId(), 0L) / MICROS_PER_CENT;

        String targetName;
        String targetEmailOrSlug;
        if (o.getTargetType() == QuotaOverrideTargetType.USER) {
            User u = ctx.usersById().get(o.getTargetId());
            targetName = u != null ? u.getFullName() : null;
            targetEmailOrSlug = u != null ? u.getEmail() : null;
        } else {
            targetName = org != null ? org.getName() : null;
            targetEmailOrSlug = org != null ? org.getSlug() : null;
        }

        String status = (o.getExpiresAt() != null && !o.getExpiresAt().isAfter(now)) ? "expired" : "active";

        return new QuotaOverrideResponse(
                o.getId(),
                o.getTargetType().name(),
                o.getTargetId(),
                targetName,
                targetEmailOrSlug,
                plan != null ? plan.getName() : null,
                o.getKind().name(),
                o.getAmountCents(),
                baseCap,
                effectiveBoxed,
                spendCents,
                o.getExpiresAt(),
                o.getReason(),
                o.getGrantedByName(),
                o.getCreatedAt(),
                status);
    }
}
