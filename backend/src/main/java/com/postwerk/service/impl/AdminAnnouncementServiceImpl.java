package com.postwerk.service.impl;

import com.postwerk.dto.admin.AdminAnnouncementDetailResponse;
import com.postwerk.dto.admin.AdminAnnouncementResponse;
import com.postwerk.dto.admin.AnnouncementEventResponse;
import com.postwerk.dto.admin.AnnouncementKpisResponse;
import com.postwerk.dto.admin.AnnouncementRequest;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Announcement;
import com.postwerk.model.AnnouncementEvent;
import com.postwerk.model.AuditAction;
import com.postwerk.model.enums.AnnouncementLifecycle;
import com.postwerk.model.enums.AnnouncementPlacement;
import com.postwerk.model.enums.AnnouncementType;
import com.postwerk.model.enums.AudienceScope;
import com.postwerk.repository.AnnouncementEventRepository;
import com.postwerk.repository.AnnouncementRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.service.AdminAnnouncementService;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdminAnnouncementService}. Announcements are filtered/sorted/paginated in-memory
 * (low volume), consistent with the other admin tooling; display status is derived from the
 * lifecycle + window. Publishing requires both DE and EN title + body. Each mutation appends a
 * change-history event and writes an audit entry.
 *
 * @since 1.0
 */
@Service
public class AdminAnnouncementServiceImpl implements AdminAnnouncementService {

    private final AnnouncementRepository repository;
    private final AnnouncementEventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final StaffNameResolver staffNames;
    private final AuditService auditService;

    public AdminAnnouncementServiceImpl(AnnouncementRepository repository,
                                        AnnouncementEventRepository eventRepository,
                                        OrganizationRepository organizationRepository,
                                        StaffNameResolver staffNames,
                                        AuditService auditService) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.organizationRepository = organizationRepository;
        this.staffNames = staffNames;
        this.auditService = auditService;
    }

    // ── List ────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<AdminAnnouncementResponse> list(String search, String type, String status, String audience,
                                                String placement, String sort, Pageable pageable) {
        Instant now = Instant.now();
        String q = search == null ? "" : search.trim().toLowerCase();

        List<AdminAnnouncementResponse> rows = repository.findAllByOrderByUpdatedAtDesc().stream()
                .map(a -> toResponse(a, now))
                .filter(r -> {
                    if (!q.isEmpty() && !(SafeStrings.containsIgnoreCase(r.titleDe(), q)
                            || SafeStrings.containsIgnoreCase(r.titleEn(), q)
                            || SafeStrings.containsIgnoreCase(r.id().toString(), q)
                            || SafeStrings.containsIgnoreCase(r.audienceOrgName(), q))) return false;
                    if (type != null && !type.isBlank() && !type.equalsIgnoreCase(r.type())) return false;
                    if (status != null && !status.isBlank() && !status.equalsIgnoreCase(r.status())) return false;
                    if (audience != null && !audience.isBlank() && !audience.equalsIgnoreCase(r.audience())) return false;
                    if (placement != null && !placement.isBlank() && !placement.equalsIgnoreCase(r.placement())) return false;
                    return true;
                })
                .sorted(comparator(sort, now))
                .collect(Collectors.toList());

        return InMemoryPage.of(rows, pageable);
    }

    private Comparator<AdminAnnouncementResponse> comparator(String sort, Instant now) {
        return switch (sort == null ? "window" : sort) {
            case "status" -> Comparator.comparingInt((AdminAnnouncementResponse r) -> statusRank(r.status()));
            case "updated" -> Comparator.comparing(AdminAnnouncementResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            // window: LIVE first, then soonest-scheduled, then the rest by recency.
            default -> Comparator.comparingInt((AdminAnnouncementResponse r) -> windowRank(r.status()))
                    .thenComparing(r -> r.startsAt() != null ? r.startsAt() : Instant.MAX,
                            Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private static int windowRank(String status) {
        return switch (status) { case "LIVE" -> 0; case "SCHEDULED" -> 1; case "DRAFT" -> 2; case "EXPIRED" -> 3; default -> 4; };
    }
    private static int statusRank(String status) {
        return switch (status) { case "LIVE" -> 0; case "SCHEDULED" -> 1; case "DRAFT" -> 2; case "EXPIRED" -> 3; case "ARCHIVED" -> 4; default -> 5; };
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public AnnouncementKpisResponse kpis() {
        Instant now = Instant.now();
        Instant expiredFloor = now.minus(30, ChronoUnit.DAYS);
        List<Announcement> all = repository.findAllByOrderByUpdatedAtDesc();

        long live = 0, scheduled = 0, drafts = 0, maintenanceLive = 0, expired30d = 0;
        Instant nextLive = null;
        for (Announcement a : all) {
            String s = derivedStatus(a, now);
            switch (s) {
                case "LIVE" -> { live++; if (a.getType() == AnnouncementType.MAINTENANCE) maintenanceLive++; }
                case "SCHEDULED" -> {
                    scheduled++;
                    if (a.getStartsAt() != null && (nextLive == null || a.getStartsAt().isBefore(nextLive))) nextLive = a.getStartsAt();
                }
                case "DRAFT" -> drafts++;
                case "EXPIRED" -> { if (a.getEndsAt() != null && !a.getEndsAt().isBefore(expiredFloor)) expired30d++; }
                default -> { }
            }
        }
        return new AnnouncementKpisResponse(live, scheduled, drafts, maintenanceLive, expired30d, nextLive);
    }

    // ── Detail ────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public AdminAnnouncementDetailResponse getAnnouncement(UUID id) {
        Announcement a = require(id);
        List<AnnouncementEventResponse> history = eventRepository.findByAnnouncementIdOrderByCreatedAtAsc(id).stream()
                .map(e -> new AnnouncementEventResponse(e.getLabel(), e.getActor(), e.getCreatedAt()))
                .toList();
        return new AdminAnnouncementDetailResponse(toResponse(a, Instant.now()), history);
    }

    // ── Mutations ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AdminAnnouncementResponse create(AnnouncementRequest req, UUID actorUserId, String ip) {
        String staff = staffNames.of(actorUserId);
        Announcement a = new Announcement();
        a.setLifecycle(AnnouncementLifecycle.DRAFT);
        a.setCreatedByUserId(actorUserId);
        a.setCreatedByName(staff);
        applyRequest(a, req);
        a.setUpdatedByName(staff);
        a = repository.save(a);
        addEvent(a.getId(), "Draft created", staff);
        auditService.log(actorUserId, AuditAction.ANNOUNCEMENT_CREATED, "Created announcement " + a.getTitleEn(), ip);
        return toResponse(a, Instant.now());
    }

    @Override
    @Transactional
    public AdminAnnouncementResponse update(UUID id, AnnouncementRequest req, UUID actorUserId, String ip) {
        Announcement a = require(id);
        applyRequest(a, req);
        a.setUpdatedByName(staffNames.of(actorUserId));
        repository.save(a);
        addEvent(id, "Content / settings edited", staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.ANNOUNCEMENT_UPDATED, "Updated announcement " + a.getTitleEn(), ip);
        return toResponse(a, Instant.now());
    }

    @Override
    @Transactional
    public AdminAnnouncementResponse publish(UUID id, UUID actorUserId, String ip) {
        Announcement a = require(id);
        if (isBlank(a.getTitleDe()) || isBlank(a.getTitleEn()) || isBlank(a.getBodyDe()) || isBlank(a.getBodyEn())) {
            throw new IllegalArgumentException("Both German and English title + body are required to publish.");
        }
        a.setLifecycle(AnnouncementLifecycle.PUBLISHED);
        if (a.getStartsAt() == null) a.setStartsAt(Instant.now());
        a.setUpdatedByName(staffNames.of(actorUserId));
        repository.save(a);
        addEvent(id, "Published", staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.ANNOUNCEMENT_PUBLISHED, "Published announcement " + a.getTitleEn(), ip);
        return toResponse(a, Instant.now());
    }

    @Override
    @Transactional
    public AdminAnnouncementResponse end(UUID id, UUID actorUserId, String ip) {
        Announcement a = require(id);
        if (a.getLifecycle() != AnnouncementLifecycle.PUBLISHED) {
            throw new IllegalStateException("Only a published announcement can be ended.");
        }
        a.setEndsAt(Instant.now());
        a.setUpdatedByName(staffNames.of(actorUserId));
        repository.save(a);
        addEvent(id, "Ended early", staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.ANNOUNCEMENT_ENDED, "Ended announcement " + a.getTitleEn(), ip);
        return toResponse(a, Instant.now());
    }

    @Override
    @Transactional
    public AdminAnnouncementResponse archive(UUID id, UUID actorUserId, String ip) {
        Announcement a = require(id);
        a.setLifecycle(AnnouncementLifecycle.ARCHIVED);
        a.setUpdatedByName(staffNames.of(actorUserId));
        repository.save(a);
        addEvent(id, "Archived", staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.ANNOUNCEMENT_ARCHIVED, "Archived announcement " + a.getTitleEn(), ip);
        return toResponse(a, Instant.now());
    }

    @Override
    @Transactional
    public AdminAnnouncementResponse duplicate(UUID id, UUID actorUserId, String ip) {
        Announcement src = require(id);
        String staff = staffNames.of(actorUserId);
        Announcement copy = Announcement.builder()
                .titleDe(prefixCopy(src.getTitleDe())).titleEn(prefixCopy(src.getTitleEn()))
                .bodyDe(src.getBodyDe()).bodyEn(src.getBodyEn())
                .ctaLabelDe(src.getCtaLabelDe()).ctaLabelEn(src.getCtaLabelEn()).ctaUrl(src.getCtaUrl())
                .type(src.getType()).placement(src.getPlacement()).audience(src.getAudience())
                .audiencePlans(src.getAudiencePlans()).audienceOrgId(src.getAudienceOrgId()).audienceOrgName(src.getAudienceOrgName())
                .dismissible(src.isDismissible()).lifecycle(AnnouncementLifecycle.DRAFT)
                .createdByUserId(actorUserId).createdByName(staff).updatedByName(staff)
                .build();
        copy = repository.save(copy);
        addEvent(copy.getId(), "Duplicated from " + src.getTitleEn(), staff);
        auditService.log(actorUserId, AuditAction.ANNOUNCEMENT_CREATED, "Duplicated announcement " + src.getTitleEn(), ip);
        return toResponse(copy, Instant.now());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private void applyRequest(Announcement a, AnnouncementRequest req) {
        a.setTitleDe(req.titleDe().trim());
        a.setTitleEn(req.titleEn().trim());
        a.setBodyDe(trimToNull(req.bodyDe()));
        a.setBodyEn(trimToNull(req.bodyEn()));
        a.setCtaLabelDe(trimToNull(req.ctaLabelDe()));
        a.setCtaLabelEn(trimToNull(req.ctaLabelEn()));
        a.setCtaUrl(trimToNull(req.ctaUrl()));
        a.setType(EnumUtil.parseOrThrow(AnnouncementType.class, req.type(), "type"));
        a.setPlacement(req.placement() != null ? EnumUtil.parseOrThrow(AnnouncementPlacement.class, req.placement(), "placement") : AnnouncementPlacement.BANNER);
        AudienceScope aud = req.audience() != null ? EnumUtil.parseOrThrow(AudienceScope.class, req.audience(), "audience") : AudienceScope.EVERYONE;
        a.setAudience(aud);
        a.setAudiencePlans(aud == AudienceScope.PLAN ? AudienceCsv.pack(req.audiencePlans()) : null);
        if (aud == AudienceScope.ORG && req.audienceOrgId() != null) {
            a.setAudienceOrgId(req.audienceOrgId());
            a.setAudienceOrgName(organizationRepository.findById(req.audienceOrgId()).map(o -> o.getName()).orElse(null));
        } else {
            a.setAudienceOrgId(null);
            a.setAudienceOrgName(null);
        }
        a.setDismissible(req.dismissible() == null || req.dismissible());
        a.setStartsAt(req.startsAt());
        a.setEndsAt(req.endsAt());
    }

    private Announcement require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Announcement", id.toString()));
    }

    private void addEvent(UUID announcementId, String label, String actor) {
        eventRepository.save(AnnouncementEvent.builder()
                .announcementId(announcementId).label(label).actor(actor != null ? actor : "system").build());
    }

    /** DRAFT / ARCHIVED explicit; PUBLISHED derives SCHEDULED / EXPIRED / LIVE from the window. */
    private static String derivedStatus(Announcement a, Instant now) {
        if (a.getLifecycle() == AnnouncementLifecycle.DRAFT) return "DRAFT";
        if (a.getLifecycle() == AnnouncementLifecycle.ARCHIVED) return "ARCHIVED";
        if (a.getStartsAt() != null && a.getStartsAt().isAfter(now)) return "SCHEDULED";
        if (a.getEndsAt() != null && a.getEndsAt().isBefore(now)) return "EXPIRED";
        return "LIVE";
    }

    private AdminAnnouncementResponse toResponse(Announcement a, Instant now) {
        List<String> plans = AudienceCsv.unpack(a.getAudiencePlans());
        return new AdminAnnouncementResponse(
                a.getId(), a.getTitleDe(), a.getTitleEn(), a.getBodyDe(), a.getBodyEn(),
                a.getCtaLabelDe(), a.getCtaLabelEn(), a.getCtaUrl(),
                a.getType() != null ? a.getType().name() : null,
                a.getPlacement() != null ? a.getPlacement().name() : null,
                a.getAudience() != null ? a.getAudience().name() : null,
                plans, a.getAudienceOrgId(), a.getAudienceOrgName(), a.isDismissible(),
                a.getLifecycle() != null ? a.getLifecycle().name() : null,
                derivedStatus(a, now), a.getStartsAt(), a.getEndsAt(),
                a.getCreatedByName(), a.getUpdatedByName(), a.getUpdatedAt());
    }

    private static String prefixCopy(String s) {
        if (s == null) return null;
        return ("Copy of " + s).substring(0, Math.min(200, ("Copy of " + s).length()));
    }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String trimToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
