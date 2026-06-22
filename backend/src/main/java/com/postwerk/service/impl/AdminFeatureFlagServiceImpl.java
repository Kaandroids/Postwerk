package com.postwerk.service.impl;

import com.postwerk.dto.admin.AdminFlagDetailResponse;
import com.postwerk.dto.admin.AdminFlagResponse;
import com.postwerk.dto.admin.AnnouncementEventResponse;
import com.postwerk.dto.admin.CreateFlagRequest;
import com.postwerk.dto.admin.FeatureFlagKpisResponse;
import com.postwerk.dto.admin.FlagOverrideDto;
import com.postwerk.dto.admin.UpdateFlagRequest;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.FeatureFlag;
import com.postwerk.model.FeatureFlagEvent;
import com.postwerk.model.FeatureFlagOverride;
import com.postwerk.model.enums.AudienceScope;
import com.postwerk.model.enums.FeatureFlagKind;
import com.postwerk.repository.FeatureFlagEventRepository;
import com.postwerk.repository.FeatureFlagOverrideRepository;
import com.postwerk.repository.FeatureFlagRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AdminFeatureFlagService;
import com.postwerk.service.AuditService;
import com.postwerk.service.StaffNameResolver;
import com.postwerk.util.AudienceCsv;
import com.postwerk.util.EnumUtil;
import com.postwerk.util.InMemoryPage;
import com.postwerk.util.SafeStrings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdminFeatureFlagService}. Flags are filtered/sorted/paginated in-memory (low
 * volume), consistent with the other admin tooling; display status + staleness are derived. Overrides
 * live in their own table (batch-loaded N+1-safe). Each mutation appends a change-history event +
 * audit entry. The rollout % is a stored intent — evaluation/bucketing is per-feature wiring (future).
 *
 * @since 1.0
 */
@Service
public class AdminFeatureFlagServiceImpl implements AdminFeatureFlagService {

    private static final int STALE_DAYS = 60;

    private final FeatureFlagRepository repository;
    private final FeatureFlagOverrideRepository overrideRepository;
    private final FeatureFlagEventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final StaffNameResolver staffNames;
    private final AuditService auditService;

    public AdminFeatureFlagServiceImpl(FeatureFlagRepository repository,
                                       FeatureFlagOverrideRepository overrideRepository,
                                       FeatureFlagEventRepository eventRepository,
                                       OrganizationRepository organizationRepository,
                                       StaffNameResolver staffNames,
                                       AuditService auditService) {
        this.repository = repository;
        this.overrideRepository = overrideRepository;
        this.eventRepository = eventRepository;
        this.organizationRepository = organizationRepository;
        this.staffNames = staffNames;
        this.auditService = auditService;
    }

    // ── List ────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<AdminFlagResponse> list(String search, String kind, String status, String targeting,
                                        String health, String sort, Pageable pageable) {
        Instant now = Instant.now();
        String q = search == null ? "" : search.trim().toLowerCase();
        List<FeatureFlag> all = repository.findAllByOrderByUpdatedAtDesc();

        // Batch-load overrides (N+1-safe).
        List<UUID> ids = all.stream().map(FeatureFlag::getId).toList();
        Map<UUID, List<FlagOverrideDto>> overrides = ids.isEmpty() ? Map.of()
                : overrideRepository.findByFlagIdIn(ids).stream()
                    .collect(Collectors.groupingBy(FeatureFlagOverride::getFlagId,
                            Collectors.mapping(o -> new FlagOverrideDto(o.getScope(), o.getValue()), Collectors.toList())));

        List<AdminFlagResponse> rows = all.stream()
                .map(f -> toResponse(f, overrides.getOrDefault(f.getId(), List.of()), now))
                .filter(r -> {
                    if (!q.isEmpty() && !(SafeStrings.containsIgnoreCase(r.key(), q)
                            || SafeStrings.containsIgnoreCase(r.name(), q)
                            || SafeStrings.containsIgnoreCase(r.description(), q))) return false;
                    if (kind != null && !kind.isBlank() && !kind.equalsIgnoreCase(r.kind())) return false;
                    if (status != null && !status.isBlank() && !status.equalsIgnoreCase(r.status())) return false;
                    if (targeting != null && !targeting.isBlank() && !targeting.equalsIgnoreCase(r.audience())) return false;
                    if ("stale".equalsIgnoreCase(health) && !r.stale()) return false;
                    return true;
                })
                .sorted(comparator(sort))
                .collect(Collectors.toList());

        return InMemoryPage.of(rows, pageable);
    }

    private Comparator<AdminFlagResponse> comparator(String sort) {
        return switch (sort == null ? "updated" : sort) {
            case "rollout" -> Comparator.comparingInt(AdminFlagResponse::rollout).reversed();
            case "status" -> Comparator.comparingInt((AdminFlagResponse r) -> statusRank(r.status()));
            default -> Comparator.comparing(AdminFlagResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }
    private static int statusRank(String s) {
        return switch (s) { case "ON" -> 0; case "ROLLING" -> 1; case "OFF" -> 2; case "KILLED" -> 3; case "ARCHIVED" -> 4; default -> 5; };
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public FeatureFlagKpisResponse kpis() {
        Instant now = Instant.now();
        List<FeatureFlag> all = repository.findAllByOrderByUpdatedAtDesc();
        long total = all.size(), on = 0, partial = 0, off = 0, killed = 0, archived = 0, stale = 0;
        for (FeatureFlag f : all) {
            switch (statusOf(f)) {
                case "ON" -> on++;
                case "ROLLING" -> partial++;
                case "OFF" -> off++;
                case "KILLED" -> killed++;
                case "ARCHIVED" -> archived++;
                default -> { }
            }
            if (isStale(f, now)) stale++;
        }
        return new FeatureFlagKpisResponse(total, on, partial, off, killed, archived, stale, partial);
    }

    // ── Detail ────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public AdminFlagDetailResponse getFlag(UUID id) {
        FeatureFlag f = require(id);
        List<AnnouncementEventResponse> history = eventRepository.findByFlagIdOrderByCreatedAtAsc(id).stream()
                .map(e -> new AnnouncementEventResponse(e.getLabel(), e.getActor(), e.getCreatedAt()))
                .toList();
        return new AdminFlagDetailResponse(toResponse(f, loadOverrides(id), Instant.now()), history);
    }

    // ── Mutations ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AdminFlagResponse create(CreateFlagRequest req, UUID actorUserId, String ip) {
        String key = req.key().trim().toLowerCase();
        if (repository.existsByFlagKey(key)) throw new IllegalArgumentException("A flag with key '" + key + "' already exists.");
        String staff = staffNames.of(actorUserId);
        FeatureFlag f = FeatureFlag.builder()
                .flagKey(key).name(req.name().trim()).description(trimToNull(req.description()))
                .kind(EnumUtil.parseOrThrow(FeatureFlagKind.class, req.kind(), "kind"))
                .enabled(false).rollout(0).audience(AudienceScope.EVERYONE)
                .createdByName(staff).updatedByName(staff)
                .build();
        f = repository.save(f);
        addEvent(f.getId(), "Created · off", staff);
        auditService.log(actorUserId, AuditAction.FEATURE_FLAG_CREATED, "Created flag " + key, ip);
        return toResponse(f, List.of(), Instant.now());
    }

    @Override
    @Transactional
    public AdminFlagResponse update(UUID id, UpdateFlagRequest req, UUID actorUserId, String ip) {
        FeatureFlag f = require(id);
        f.setName(req.name().trim());
        f.setDescription(trimToNull(req.description()));
        f.setKind(EnumUtil.parseOrThrow(FeatureFlagKind.class, req.kind(), "kind"));
        f.setEnabled(req.enabled());
        f.setRollout(Math.max(0, Math.min(100, req.rollout())));
        AudienceScope aud = req.audience() != null ? EnumUtil.parseOrThrow(AudienceScope.class, req.audience(), "audience") : AudienceScope.EVERYONE;
        f.setAudience(aud);
        f.setAudiencePlans(aud == AudienceScope.PLAN ? AudienceCsv.pack(req.audiencePlans()) : null);
        if (aud == AudienceScope.ORG && req.audienceOrgId() != null) {
            f.setAudienceOrgId(req.audienceOrgId());
            f.setAudienceOrgName(organizationRepository.findById(req.audienceOrgId()).map(o -> o.getName()).orElse(null));
        } else { f.setAudienceOrgId(null); f.setAudienceOrgName(null); }
        touchOnSince(f);
        f.setUpdatedByName(staffNames.of(actorUserId));
        repository.save(f);
        replaceOverrides(id, req.overrides());
        addEvent(id, "Settings saved · " + statusOf(f).toLowerCase() + " · " + f.getRollout() + "%", staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.FEATURE_FLAG_UPDATED, "Updated flag " + f.getFlagKey(), ip);
        return toResponse(f, loadOverrides(id), Instant.now());
    }

    @Override
    @Transactional
    public AdminFlagResponse setEnabled(UUID id, boolean enabled, UUID actorUserId, String ip) {
        FeatureFlag f = require(id);
        if (f.isKilled() || f.isArchived()) throw new IllegalStateException("Restore the flag before toggling it.");
        f.setEnabled(enabled);
        f.setRollout(enabled ? 100 : 0);
        touchOnSince(f);
        f.setUpdatedByName(staffNames.of(actorUserId));
        repository.save(f);
        addEvent(id, enabled ? "Enabled · 100%" : "Disabled · 0%", staffNames.of(actorUserId));
        auditService.log(actorUserId, enabled ? AuditAction.FEATURE_FLAG_ENABLED : AuditAction.FEATURE_FLAG_DISABLED,
                (enabled ? "Enabled flag " : "Disabled flag ") + f.getFlagKey(), ip);
        return toResponse(f, loadOverrides(id), Instant.now());
    }

    @Override
    @Transactional
    public AdminFlagResponse kill(UUID id, UUID actorUserId, String ip) {
        FeatureFlag f = require(id);
        f.setKilled(true);
        f.setEnabled(false);
        // Force the rollout to 0 too, so any code reading enabled/rollout directly (not the derived
        // status) can never treat a killed flag as still partially on.
        f.setRollout(0);
        f.setOnSinceAt(null);
        f.setUpdatedByName(staffNames.of(actorUserId));
        repository.save(f);
        addEvent(id, "KILLED — forced off & locked", staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.FEATURE_FLAG_KILLED, "Kill-switched flag " + f.getFlagKey(), ip);
        return toResponse(f, loadOverrides(id), Instant.now());
    }

    @Override
    @Transactional
    public AdminFlagResponse restore(UUID id, UUID actorUserId, String ip) {
        FeatureFlag f = require(id);
        f.setKilled(false);
        f.setArchived(false);
        touchOnSince(f);
        f.setUpdatedByName(staffNames.of(actorUserId));
        repository.save(f);
        addEvent(id, "Restored", staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.FEATURE_FLAG_RESTORED, "Restored flag " + f.getFlagKey(), ip);
        return toResponse(f, loadOverrides(id), Instant.now());
    }

    @Override
    @Transactional
    public AdminFlagResponse archive(UUID id, UUID actorUserId, String ip) {
        FeatureFlag f = require(id);
        f.setArchived(true);
        f.setEnabled(false);
        f.setRollout(0);
        f.setOnSinceAt(null);
        f.setUpdatedByName(staffNames.of(actorUserId));
        repository.save(f);
        addEvent(id, "Archived", staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.FEATURE_FLAG_ARCHIVED, "Archived flag " + f.getFlagKey(), ip);
        return toResponse(f, loadOverrides(id), Instant.now());
    }

    @Override
    @Transactional
    public AdminFlagResponse duplicate(UUID id, UUID actorUserId, String ip) {
        FeatureFlag src = require(id);
        String staff = staffNames.of(actorUserId);
        String key = uniqueKey(src.getFlagKey() + "-copy");
        FeatureFlag copy = FeatureFlag.builder()
                .flagKey(key).name("Copy of " + src.getName()).description(src.getDescription())
                .kind(src.getKind()).enabled(false).rollout(0)
                .audience(src.getAudience()).audiencePlans(src.getAudiencePlans())
                .audienceOrgId(src.getAudienceOrgId()).audienceOrgName(src.getAudienceOrgName())
                .createdByName(staff).updatedByName(staff)
                .build();
        copy = repository.save(copy);
        for (FeatureFlagOverride o : overrideRepository.findByFlagId(id)) {
            overrideRepository.save(FeatureFlagOverride.builder().flagId(copy.getId()).scope(o.getScope()).value(o.getValue()).build());
        }
        addEvent(copy.getId(), "Duplicated from " + src.getFlagKey() + " · off", staff);
        auditService.log(actorUserId, AuditAction.FEATURE_FLAG_CREATED, "Duplicated flag " + src.getFlagKey() + " → " + key, ip);
        return toResponse(copy, loadOverrides(copy.getId()), Instant.now());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private FeatureFlag require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", id.toString()));
    }

    /** Stamp/clear onSinceAt when the flag enters/leaves the fully-on state. */
    private static void touchOnSince(FeatureFlag f) {
        boolean fullyOn = f.isEnabled() && !f.isKilled() && !f.isArchived()
                && f.getRollout() == 100 && f.getAudience() == AudienceScope.EVERYONE;
        if (fullyOn) { if (f.getOnSinceAt() == null) f.setOnSinceAt(Instant.now()); }
        else f.setOnSinceAt(null);
    }

    private void replaceOverrides(UUID flagId, List<FlagOverrideDto> overrides) {
        overrideRepository.deleteByFlagId(flagId);
        if (overrides != null) {
            for (FlagOverrideDto o : overrides) {
                if (o.scope() == null || o.scope().isBlank()) continue;
                overrideRepository.save(FeatureFlagOverride.builder()
                        .flagId(flagId).scope(o.scope().trim())
                        .value("off".equalsIgnoreCase(o.value()) ? "off" : "on").build());
            }
        }
    }

    private List<FlagOverrideDto> loadOverrides(UUID flagId) {
        return overrideRepository.findByFlagId(flagId).stream()
                .map(o -> new FlagOverrideDto(o.getScope(), o.getValue())).toList();
    }

    private String uniqueKey(String base) {
        String key = base;
        int n = 2;
        while (repository.existsByFlagKey(key)) key = base + "-" + (n++);
        return key;
    }

    private void addEvent(UUID flagId, String label, String actor) {
        eventRepository.save(FeatureFlagEvent.builder()
                .flagId(flagId).label(label).actor(actor != null ? actor : "system").build());
    }


    private static String statusOf(FeatureFlag f) {
        if (f.isArchived()) return "ARCHIVED";
        if (f.isKilled()) return "KILLED";
        if (!f.isEnabled() || f.getRollout() == 0) return "OFF";
        if (f.getRollout() == 100 && f.getAudience() == AudienceScope.EVERYONE) return "ON";
        return "ROLLING";
    }

    private static boolean isStale(FeatureFlag f, Instant now) {
        return f.isEnabled() && !f.isKilled() && !f.isArchived()
                && f.getRollout() == 100 && f.getAudience() == AudienceScope.EVERYONE
                && f.getOnSinceAt() != null && f.getOnSinceAt().isBefore(now.minus(STALE_DAYS, ChronoUnit.DAYS));
    }

    private AdminFlagResponse toResponse(FeatureFlag f, List<FlagOverrideDto> overrides, Instant now) {
        List<String> plans = AudienceCsv.unpack(f.getAudiencePlans());
        return new AdminFlagResponse(
                f.getId(), f.getFlagKey(), f.getName(), f.getDescription(),
                f.getKind() != null ? f.getKind().name() : null,
                f.isEnabled(), f.getRollout(),
                f.getAudience() != null ? f.getAudience().name() : null,
                plans, f.getAudienceOrgId(), f.getAudienceOrgName(), new ArrayList<>(overrides),
                f.isKilled(), f.isArchived(), isStale(f, now), statusOf(f),
                f.getUpdatedByName(), f.getUpdatedAt());
    }

    private static String trimToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
