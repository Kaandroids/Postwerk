package com.postwerk.service.impl;

import com.postwerk.config.GdprProperties;
import com.postwerk.dto.admin.AdminDataRequestDetailResponse;
import com.postwerk.dto.admin.AdminDataRequestResponse;
import com.postwerk.dto.admin.CreateDataRequestRequest;
import com.postwerk.dto.admin.DataFootprintResponse;
import com.postwerk.dto.admin.DataRequestTimelineEntry;
import com.postwerk.dto.admin.GdprKpisResponse;
import com.postwerk.dto.admin.RetentionPostureResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.DataRequest;
import com.postwerk.model.DataRequestEvent;
import com.postwerk.model.JobRun;
import com.postwerk.model.Organization;
import com.postwerk.model.User;
import com.postwerk.model.enums.DataRequestChannel;
import com.postwerk.model.enums.DataRequestStatus;
import com.postwerk.model.enums.DataRequestType;
import com.postwerk.repository.AiConversationRepository;
import com.postwerk.repository.AuditLogRepository;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.DataRequestEventRepository;
import com.postwerk.repository.DataRequestRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.EmailRepository;
import com.postwerk.repository.JobRunRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AdminGdprService;
import com.postwerk.service.AuditService;
import com.postwerk.service.DataRetentionService;
import com.postwerk.service.RefreshTokenService;
import com.postwerk.service.StaffNameResolver;
import com.postwerk.util.EnumUtil;
import com.postwerk.util.InMemoryPage;
import com.postwerk.util.SafeStrings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdminGdprService}. The DSAR queue is filtered/sorted/paginated in-memory (DSAR
 * volume is low), consistent with the other admin tooling. The retention posture comes from real
 * {@link GdprProperties} + the last {@code data-retention} job run; footprint counts come from the
 * existing per-user repositories.
 *
 * <p><b>Honest scope:</b> there is no export-package builder yet — {@code runExport} records the
 * intent + audit entry (a real generator is future work). {@code executeErasure} irreversibly
 * pseudonymizes the subject's directly-identifying PII (user + DSAR record) in-transaction, revokes
 * sessions and soft-deletes the account; bulk content (emails, conversations) and the final row
 * hard-delete then run via the nightly retention sweep. Erasure is irreversible.
 *
 * @since 1.0
 */
@Service
public class AdminGdprServiceImpl implements AdminGdprService {

    private static final int DEADLINE_DAYS = 30;

    private final DataRequestRepository requestRepository;
    private final DataRequestEventRepository eventRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmailRepository emailRepository;
    private final AutomationRepository automationRepository;
    private final AiConversationRepository conversationRepository;
    private final AuditLogRepository auditLogRepository;
    private final JobRunRepository jobRunRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final StaffNameResolver staffNames;
    private final GdprProperties gdprProperties;

    public AdminGdprServiceImpl(DataRequestRepository requestRepository,
                                DataRequestEventRepository eventRepository,
                                UserRepository userRepository,
                                OrganizationRepository organizationRepository,
                                EmailAccountRepository emailAccountRepository,
                                EmailRepository emailRepository,
                                AutomationRepository automationRepository,
                                AiConversationRepository conversationRepository,
                                AuditLogRepository auditLogRepository,
                                JobRunRepository jobRunRepository,
                                RefreshTokenService refreshTokenService,
                                AuditService auditService,
                                StaffNameResolver staffNames,
                                GdprProperties gdprProperties) {
        this.requestRepository = requestRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.emailRepository = emailRepository;
        this.automationRepository = automationRepository;
        this.conversationRepository = conversationRepository;
        this.auditLogRepository = auditLogRepository;
        this.jobRunRepository = jobRunRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.staffNames = staffNames;
        this.gdprProperties = gdprProperties;
    }

    // ── List ────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<AdminDataRequestResponse> listRequests(String search, String type, String status, String deadline,
                                                       String sort, String dir, Pageable pageable) {
        Instant now = Instant.now();
        String q = search == null ? "" : search.trim().toLowerCase();

        List<DataRequest> filtered = requestRepository.findAllByOrderByRequestedAtDesc().stream()
                .filter(r -> {
                    if (!q.isEmpty() && !(SafeStrings.containsIgnoreCase(r.getSubjectName(), q)
                            || SafeStrings.containsIgnoreCase(r.getSubjectEmail(), q)
                            || SafeStrings.containsIgnoreCase(r.getOrgName(), q)
                            || SafeStrings.containsIgnoreCase(r.getId().toString(), q))) return false;
                    if (type != null && !type.isBlank() && (r.getType() == null || !r.getType().name().equalsIgnoreCase(type))) return false;
                    if (status != null && !status.isBlank() && (r.getStatus() == null || !r.getStatus().name().equalsIgnoreCase(status))) return false;
                    if (deadline != null && !deadline.isBlank()) {
                        if (!isOpen(r)) return false;
                        String ds = deadlineState(r, now);
                        if (!deadline.equalsIgnoreCase(ds)) return false;
                    }
                    return true;
                })
                .sorted(comparator(sort, dir, now))
                .collect(Collectors.toList());

        List<AdminDataRequestResponse> rows = filtered.stream().map(this::toResponse).collect(Collectors.toList());
        return InMemoryPage.of(rows, pageable);
    }

    private Comparator<DataRequest> comparator(String sort, String dir, Instant now) {
        boolean desc = "desc".equalsIgnoreCase(dir);
        Comparator<DataRequest> base = switch (sort == null ? "deadline" : sort) {
            case "requested" -> Comparator.comparing(DataRequest::getRequestedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "status" -> Comparator.comparingInt(r -> r.getStatus() == null ? 99 : r.getStatus().ordinal());
            // deadline: open requests by deadline (soonest/most-overdue first); closed sink to the end.
            default -> Comparator.comparingLong(r -> isOpen(r) ? r.getDeadlineAt().toEpochMilli() : Long.MAX_VALUE);
        };
        return desc ? base.reversed() : base;
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public GdprKpisResponse kpis() {
        Instant now = Instant.now();
        Instant soon = now.plus(7, ChronoUnit.DAYS);
        Instant closedFloor = now.minus(30, ChronoUnit.DAYS);
        List<DataRequest> all = requestRepository.findAllByOrderByRequestedAtDesc();

        long pending = all.stream().filter(r -> r.getStatus() == DataRequestStatus.PENDING).count();
        long inProgress = all.stream().filter(r -> r.getStatus() == DataRequestStatus.IN_PROGRESS).count();
        long open = pending + inProgress;
        long overdue = all.stream().filter(r -> isOpen(r) && r.getDeadlineAt().isBefore(now)).count();
        long dueSoon = all.stream().filter(r -> isOpen(r) && !r.getDeadlineAt().isBefore(now) && !r.getDeadlineAt().isAfter(soon)).count();

        List<DataRequest> closed30 = all.stream()
                .filter(r -> (r.getStatus() == DataRequestStatus.COMPLETED || r.getStatus() == DataRequestStatus.REJECTED)
                        && r.getClosedAt() != null && !r.getClosedAt().isBefore(closedFloor))
                .toList();
        Integer avg = null;
        if (!closed30.isEmpty()) {
            double mean = closed30.stream()
                    .mapToLong(r -> Math.max(0, Duration.between(r.getRequestedAt(), r.getClosedAt()).toDays()))
                    .average().orElse(0);
            avg = (int) Math.round(mean);
        }
        return new GdprKpisResponse(open, overdue, dueSoon, closed30.size(), avg, pending, inProgress);
    }

    // ── Retention posture ─────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public RetentionPostureResponse retention() {
        JobRun last = jobRunRepository.findTopByJobIdOrderByStartedAtDesc(DataRetentionService.JOB_ID);
        return new RetentionPostureResponse(
                gdprProperties.emailRetentionDays(),
                gdprProperties.conversationRetentionDays(),
                gdprProperties.ipRetentionDays(),
                gdprProperties.auditLogRetentionDays(),
                last != null ? last.getStartedAt() : null);
    }

    // ── Detail ────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public AdminDataRequestDetailResponse getRequest(UUID id) {
        DataRequest r = require(id);
        DataFootprintResponse footprint = footprint(r.getSubjectUserId());
        List<DataRequestTimelineEntry> timeline = eventRepository.findByRequestIdOrderByCreatedAtAsc(id).stream()
                .map(e -> new DataRequestTimelineEntry(e.getLabel(), e.getActor(), e.getCreatedAt()))
                .toList();
        return new AdminDataRequestDetailResponse(toResponse(r), footprint, timeline);
    }

    private DataFootprintResponse footprint(UUID subjectUserId) {
        if (subjectUserId == null) return new DataFootprintResponse(0, 0, 0, 0, 0);
        return new DataFootprintResponse(
                emailAccountRepository.countByUserId(subjectUserId),
                emailRepository.countByUserId(subjectUserId),
                automationRepository.countByUserId(subjectUserId),
                conversationRepository.countByUserId(subjectUserId),
                auditLogRepository.countByUserId(subjectUserId));
    }

    // ── Create ──────────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AdminDataRequestResponse create(CreateDataRequestRequest req, UUID actorUserId, String ip) {
        DataRequestType type = EnumUtil.parseOrThrow(DataRequestType.class, req.type(), "type");
        DataRequestChannel channel = EnumUtil.parseOrThrow(DataRequestChannel.class, req.channel(), "channel");
        String email = req.subjectEmail().trim();

        Optional<User> subject = userRepository.findByEmail(email);
        UUID subjectUserId = subject.map(User::getId).orElse(null);
        UUID orgId = null;
        String orgName = null;
        if (subjectUserId != null) {
            Optional<Organization> personal = organizationRepository.findByOwnerUserIdAndPersonalTrue(subjectUserId);
            orgId = personal.map(Organization::getId).orElse(null);
            orgName = personal.map(Organization::getName).orElse(null);
        }

        Instant now = Instant.now();
        DataRequest r = DataRequest.builder()
                .subjectUserId(subjectUserId)
                .subjectName(req.subjectName().trim())
                .subjectEmail(email)
                .organizationId(orgId)
                .orgName(orgName)
                .type(type)
                .status(DataRequestStatus.PENDING)
                .channel(channel)
                .note(req.note() != null ? req.note().trim() : null)
                .requestedAt(now)
                .deadlineAt(now.plus(DEADLINE_DAYS, ChronoUnit.DAYS))
                .build();
        r = requestRepository.save(r);

        addEvent(r.getId(), "Request logged (" + channel.name().toLowerCase().replace('_', '-') + ")"
                + (subjectUserId == null ? " · no matching account" : ""), staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.DATA_REQUEST_CREATED,
                "Logged DSAR " + type.name() + " for " + email, ip);
        return toResponse(r);
    }

    // ── Mutations ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public AdminDataRequestResponse runExport(UUID id, UUID actorUserId, String ip) {
        DataRequest r = requireOpen(id);
        assignHandler(r, actorUserId);
        if (r.getStatus() == DataRequestStatus.PENDING) r.setStatus(DataRequestStatus.IN_PROGRESS);
        requestRepository.save(r);
        addEvent(id, "Export package build started", staffNames.of(actorUserId));
        // Honest: the export-package generator is not built yet — this records intent, not delivery.
        auditService.log(actorUserId, AuditAction.DATA_REQUEST_EXPORTED,
                "Started export build for subject user " + r.getSubjectUserId() + " (generator pending)", ip);
        return toResponse(r);
    }

    @Override
    @Transactional
    public AdminDataRequestResponse executeErasure(UUID id, UUID actorUserId, String ip) {
        DataRequest r = requireOpen(id);
        if (r.getSubjectUserId() == null) {
            throw new IllegalArgumentException("No matching account for this subject — nothing to erase.");
        }
        // Irreversibly erase the subject's directly-identifying data IN THIS TRANSACTION: pseudonymize
        // the user's PII columns, soft-delete the account, and revoke sessions. The JWT filter re-loads
        // the user on every request and rejects soft-deleted accounts (CustomUserDetailsService throws
        // DisabledException), so already-issued access tokens stop authenticating immediately. Bulk
        // content (emails, conversations) is purged by the nightly data-retention sweep, which then
        // hard-deletes the soft-deleted user row past the grace period.
        userRepository.findById(r.getSubjectUserId()).ifPresent(u -> {
            String originalEmail = u.getEmail();
            if (u.getDeletedAt() == null) {
                u.setDeletedAt(Instant.now());
                u.setDeletionReason("GDPR_ERASURE");
            }
            pseudonymizeUser(u);
            userRepository.save(u);
            refreshTokenService.revokeAllForUser(originalEmail);
        });
        // Strip the PII the DSAR record itself retained, so the queue stops holding erased data.
        r.setSubjectName("[erased]");
        r.setSubjectEmail("erased-" + r.getSubjectUserId() + "@deleted.invalid");
        assignHandler(r, actorUserId);
        r.setStatus(DataRequestStatus.COMPLETED);
        r.setClosedAt(Instant.now());
        requestRepository.save(r);
        addEvent(id, "Erasure executed · PII pseudonymized · sessions revoked", staffNames.of(actorUserId));
        // Reference the subject by id, not email: the audit log is retained 730d and must not re-introduce
        // the PII we just erased.
        auditService.log(actorUserId, AuditAction.DATA_REQUEST_ERASED,
                "Executed erasure for subject user " + r.getSubjectUserId(), ip);
        return toResponse(r);
    }

    /**
     * Overwrites the user's directly-identifying fields with non-reversible placeholders, keeping the
     * NOT-NULL constraints satisfied. The account is already disabled via {@code deletedAt}; the row is
     * hard-deleted later by the retention sweep.
     */
    private void pseudonymizeUser(User u) {
        u.setEmail("erased-" + u.getId() + "@deleted.invalid");
        u.setFullName("[erased]");
        u.setCompany(null);
        u.setPhone(null);
        u.setLastLoginIp(null);
        u.setPasswordHash("ERASED");
    }

    @Override
    @Transactional
    public AdminDataRequestResponse reject(UUID id, String reason, UUID actorUserId, String ip) {
        DataRequest r = requireOpen(id);
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("A rejection reason is required.");
        assignHandler(r, actorUserId);
        r.setStatus(DataRequestStatus.REJECTED);
        r.setRejectReason(clean);
        r.setClosedAt(Instant.now());
        requestRepository.save(r);
        addEvent(id, "Request rejected (reason logged)", staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.DATA_REQUEST_REJECTED,
                "Rejected DSAR for " + r.getSubjectEmail() + " · " + clean, ip);
        return toResponse(r);
    }

    @Override
    @Transactional
    public AdminDataRequestResponse markComplete(UUID id, UUID actorUserId, String ip) {
        DataRequest r = requireOpen(id);
        assignHandler(r, actorUserId);
        r.setStatus(DataRequestStatus.COMPLETED);
        r.setClosedAt(Instant.now());
        requestRepository.save(r);
        addEvent(id, "Marked complete", staffNames.of(actorUserId));
        auditService.log(actorUserId, AuditAction.DATA_REQUEST_COMPLETED,
                "Completed DSAR for " + r.getSubjectEmail(), ip);
        return toResponse(r);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private DataRequest require(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataRequest", id.toString()));
    }

    private DataRequest requireOpen(UUID id) {
        DataRequest r = require(id);
        if (!isOpen(r)) throw new IllegalStateException("This request is already closed.");
        return r;
    }

    private void assignHandler(DataRequest r, UUID actorUserId) {
        if (r.getHandlerUserId() == null) {
            r.setHandlerUserId(actorUserId);
            r.setHandlerName(staffNames.of(actorUserId));
        }
    }

    private void addEvent(UUID requestId, String label, String actor) {
        eventRepository.save(DataRequestEvent.builder()
                .requestId(requestId)
                .label(label)
                .actor(actor != null ? actor : "system")
                .build());
    }

    private static boolean isOpen(DataRequest r) {
        return r.getStatus() == DataRequestStatus.PENDING || r.getStatus() == DataRequestStatus.IN_PROGRESS;
    }

    private static String deadlineState(DataRequest r, Instant now) {
        long days = Duration.between(now, r.getDeadlineAt()).toDays();
        if (r.getDeadlineAt().isBefore(now)) return "overdue";
        return days <= 7 ? "due-soon" : "ok";
    }

    private AdminDataRequestResponse toResponse(DataRequest r) {
        return new AdminDataRequestResponse(
                r.getId(), r.getSubjectName(), r.getSubjectEmail(), r.getOrgName(),
                r.getType() != null ? r.getType().name() : null,
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getChannel() != null ? r.getChannel().name() : null,
                r.getRequestedAt(), r.getDeadlineAt(), r.getClosedAt(),
                r.getHandlerName(), r.getNote(), r.getRejectReason());
    }
}
