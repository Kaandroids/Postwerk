package com.postwerk.service.impl;

import com.postwerk.dto.admin.AdminMailboxHealthDetailResponse;
import com.postwerk.dto.admin.AdminMailboxHealthResponse;
import com.postwerk.dto.admin.EmailClusterSummaryResponse;
import com.postwerk.dto.admin.EmailHealthKpisResponse;
import com.postwerk.dto.admin.MailboxSyncAttemptResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.EmailAccount;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AdminEmailHealthService;
import com.postwerk.service.AuditService;
import com.postwerk.service.EmailSyncService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdminEmailHealthService}. Health is a field derived from each mailbox's persisted
 * {@code lastSyncStatus}; filtering/sorting/paging is done in-memory over all mailboxes (the same
 * approach as the design prototype) since "health" has no single column to query on. Org + owner
 * names are batch-resolved to avoid N+1.
 *
 * @since 1.0
 */
@Service
public class AdminEmailHealthServiceImpl implements AdminEmailHealthService {

    private static final long STALE_MINUTES = 1440;   // 24h
    private static final long RECENT_MINUTES = 60;    // 1h
    private static final String UNASSIGNED = "unassigned";

    private final EmailAccountRepository emailAccountRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final EmailSyncService emailSyncService;
    private final AuditService auditService;

    public AdminEmailHealthServiceImpl(EmailAccountRepository emailAccountRepository,
                                       OrganizationRepository organizationRepository,
                                       UserRepository userRepository,
                                       EmailSyncService emailSyncService,
                                       AuditService auditService) {
        this.emailAccountRepository = emailAccountRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.emailSyncService = emailSyncService;
        this.auditService = auditService;
    }

    /** A mailbox plus its derived health fields + resolved owner/org names. */
    private record Row(EmailAccount a, String orgName, String ownerEmail,
                       String health, Long ago, boolean stale, String server) {}

    // ── List ────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<AdminMailboxHealthResponse> listMailboxes(String search, String protocol, String health,
                                                          String server, String sync, Pageable pageable) {
        String q = search == null ? "" : search.trim().toLowerCase();
        List<Row> rows = loadRows().stream().filter(r -> {
            if (!q.isEmpty() && !(contains(r.a.getEmail(), q) || contains(r.ownerEmail, q)
                    || contains(r.orgName, q) || r.a.getId().toString().contains(q))) {
                return false;
            }
            if (protocol != null && !protocol.isBlank() && !protocolsOf(r.a).contains(protocol)) {
                return false;
            }
            if (health != null && !health.isBlank()) {
                if ("paused".equals(health)) {
                    if (!r.a.isPaused()) return false;
                } else if (!health.equals(r.health)) {
                    return false;
                }
            }
            if (server != null && !server.isBlank() && !server.equals(r.server)) {
                return false;
            }
            if (sync != null && !sync.isBlank()) {
                if ("recent".equals(sync) && (r.ago == null || r.ago > RECENT_MINUTES)) return false;
                if ("stale".equals(sync) && !r.stale) return false;
            }
            return true;
        }).sorted(SEVERITY_DESC).collect(Collectors.toList());

        int total = rows.size();
        int start = (int) pageable.getOffset();
        if (start >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        int end = Math.min(start + pageable.getPageSize(), total);
        List<AdminMailboxHealthResponse> content = rows.subList(start, end).stream()
                .map(this::toRowResponse).toList();
        return new PageImpl<>(content, pageable, total);
    }

    /** Default order: most severe first (failing > auth_error > ok), then stalest, then email. */
    private static final Comparator<Row> SEVERITY_DESC =
            Comparator.comparingInt((Row r) -> severityRank(r.health)).reversed()
                    .thenComparing(r -> r.ago == null ? -1L : r.ago, Comparator.reverseOrder())
                    .thenComparing(r -> r.a.getEmail() == null ? "" : r.a.getEmail());

    private static int severityRank(String health) {
        return switch (health) {
            case "failing" -> 2;
            case "auth_error" -> 1;
            default -> 0;
        };
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public EmailHealthKpisResponse kpis() {
        List<Row> rows = loadRows();
        long total = rows.size();
        long failing = rows.stream().filter(r -> "failing".equals(r.health)).count();
        long authErrors = rows.stream().filter(r -> "auth_error".equals(r.health)).count();
        long paused = rows.stream().filter(r -> r.a.isPaused()).count();
        long healthy = rows.stream().filter(r -> "ok".equals(r.health) && !r.a.isPaused()).count();
        List<Long> lags = rows.stream()
                .filter(r -> "ok".equals(r.health) && r.ago != null)
                .map(Row::ago).toList();
        Long avg = lags.isEmpty() ? null
                : Math.round(lags.stream().mapToLong(Long::longValue).average().orElse(0));
        return new EmailHealthKpisResponse(total, healthy, failing, authErrors, paused, avg);
    }

    // ── By-cluster ────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<EmailClusterSummaryResponse> clusters() {
        Map<String, List<Row>> byHost = loadRows().stream().collect(Collectors.groupingBy(Row::server));
        List<EmailClusterSummaryResponse> out = new ArrayList<>();
        byHost.forEach((host, g) -> {
            long t = g.size();
            long failing = g.stream().filter(r -> "failing".equals(r.health)).count();
            long bad = g.stream().filter(r -> !"ok".equals(r.health)).count();
            long healthy = g.stream().filter(r -> "ok".equals(r.health) && !r.a.isPaused()).count();
            String status = (failing >= 2 || bad >= 3) ? "down" : (bad > 0 ? "warn" : "ok");
            out.add(new EmailClusterSummaryResponse(host, healthy, t, failing, bad, status));
        });
        out.sort(Comparator.comparing(EmailClusterSummaryResponse::host));
        return out;
    }

    // ── Detail ────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public AdminMailboxHealthDetailResponse getMailbox(UUID mailboxId) {
        return toDetailResponse(resolveRow(require(mailboxId)));
    }

    // ── Actions (INFRA_MANAGE) ──────────────────────────────────────────────────
    @Override
    @Transactional
    public AdminMailboxHealthResponse resync(UUID mailboxId, UUID actorUserId, String ip) {
        EmailAccount a = require(mailboxId);
        emailSyncService.syncInBackground(a);
        auditService.log(actorUserId, AuditAction.MAILBOX_RESYNC_TRIGGERED, "Re-sync queued for " + a.getEmail(), ip);
        return toRowResponse(resolveRow(a));
    }

    @Override
    @Transactional
    public AdminMailboxHealthDetailResponse pause(UUID mailboxId, UUID actorUserId, String ip) {
        EmailAccount a = require(mailboxId);
        if (!a.isPaused()) {
            a.setPaused(true);
            emailAccountRepository.save(a);
            auditService.log(actorUserId, AuditAction.MAILBOX_PAUSED, "Paused mailbox " + a.getEmail(), ip);
        }
        return toDetailResponse(resolveRow(a));
    }

    @Override
    @Transactional
    public AdminMailboxHealthDetailResponse resume(UUID mailboxId, UUID actorUserId, String ip) {
        EmailAccount a = require(mailboxId);
        if (a.isPaused()) {
            a.setPaused(false);
            emailAccountRepository.save(a);
            auditService.log(actorUserId, AuditAction.MAILBOX_RESUMED, "Resumed mailbox " + a.getEmail(), ip);
        }
        return toDetailResponse(resolveRow(a));
    }

    // ── Loading + derivation ────────────────────────────────────────────────────
    private List<Row> loadRows() {
        List<EmailAccount> accounts = emailAccountRepository.findAll();
        if (accounts.isEmpty()) return List.of();

        Set<UUID> orgIds = accounts.stream().map(EmailAccount::getOrganizationId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> orgNames = new HashMap<>();
        if (!orgIds.isEmpty()) {
            organizationRepository.findAllById(orgIds).forEach(o -> orgNames.put(o.getId(), o.getName()));
        }
        Set<UUID> userIds = accounts.stream().map(EmailAccount::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> ownerEmails = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> ownerEmails.put(u.getId(), u.getEmail()));
        }

        Instant now = Instant.now();
        return accounts.stream()
                .map(a -> buildRow(a, orgNames.get(a.getOrganizationId()), ownerEmails.get(a.getUserId()), now))
                .toList();
    }

    /** Resolves a single mailbox's row (owner/org names) for the detail + action paths. */
    private Row resolveRow(EmailAccount a) {
        String orgName = a.getOrganizationId() == null ? null
                : organizationRepository.findById(a.getOrganizationId()).map(o -> o.getName()).orElse(null);
        String ownerEmail = a.getUserId() == null ? null
                : userRepository.findById(a.getUserId()).map(u -> u.getEmail()).orElse(null);
        return buildRow(a, orgName, ownerEmail, Instant.now());
    }

    private Row buildRow(EmailAccount a, String orgName, String ownerEmail, Instant now) {
        Long ago = a.getLastSyncAt() == null ? null
                : Math.max(0, Duration.between(a.getLastSyncAt(), now).toMinutes());
        boolean stale = ago != null && ago > STALE_MINUTES;
        return new Row(a, orgName, ownerEmail, deriveHealth(a), ago, stale, serverOf(a));
    }

    /** ok (healthy / never synced) | failing (connection/TLS) | auth_error (credentials rejected). */
    private static String deriveHealth(EmailAccount a) {
        String s = a.getLastSyncStatus();
        if ("AUTH_ERROR".equals(s)) return "auth_error";
        if ("CONN_ERROR".equals(s)) return "failing";
        return "ok";
    }

    private static String serverOf(EmailAccount a) {
        if (a.getImapHost() != null && !a.getImapHost().isBlank()) return a.getImapHost();
        if (a.getSmtpHost() != null && !a.getSmtpHost().isBlank()) return a.getSmtpHost();
        return UNASSIGNED;
    }

    private static List<String> protocolsOf(EmailAccount a) {
        List<String> p = new ArrayList<>(2);
        if (a.getImapHost() != null && !a.getImapHost().isBlank()) p.add("IMAP");
        if (a.getSmtpHost() != null && !a.getSmtpHost().isBlank()) p.add("SMTP");
        return p;
    }

    private EmailAccount require(UUID id) {
        return emailAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mailbox", id.toString()));
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase().contains(lowerNeedle);
    }

    // ── Mapping ─────────────────────────────────────────────────────────────────
    private AdminMailboxHealthResponse toRowResponse(Row r) {
        EmailAccount a = r.a;
        boolean imap = a.getImapHost() != null && !a.getImapHost().isBlank();
        boolean smtp = a.getSmtpHost() != null && !a.getSmtpHost().isBlank();
        return new AdminMailboxHealthResponse(
                a.getId(), a.getEmail(), a.getDisplayName(), a.getColor(),
                a.getOrganizationId(), r.orgName, r.ownerEmail,
                protocolsOf(a), r.health, a.isPaused(),
                a.getLastSyncAt(), r.ago, r.stale, a.getLastError(),
                r.server, null, imap, smtp);
    }

    private AdminMailboxHealthDetailResponse toDetailResponse(Row r) {
        EmailAccount a = r.a;
        boolean imap = a.getImapHost() != null && !a.getImapHost().isBlank();
        boolean smtp = a.getSmtpHost() != null && !a.getSmtpHost().isBlank();
        return new AdminMailboxHealthDetailResponse(
                a.getId(), a.getEmail(), a.getDisplayName(), a.getColor(),
                a.getOrganizationId(), r.orgName, r.ownerEmail,
                protocolsOf(a), r.health, a.isPaused(),
                a.getImapHost(), a.getImapPort(), a.getImapSsl(), a.isReadEnabled(),
                a.getSmtpHost(), a.getSmtpPort(), a.getSmtpSsl(), a.isWriteEnabled(),
                r.server, a.getLastSyncAt(), r.ago, r.stale, a.getLastError(), a.getLastErrorAt(),
                null, a.getCreatedAt(), recentAttempts(a));
    }

    /** Synthesizes the recent-attempts timeline from the single persisted last attempt. */
    private static List<MailboxSyncAttemptResponse> recentAttempts(EmailAccount a) {
        String s = a.getLastSyncStatus();
        if (s == null) return List.of();
        if ("OK".equals(s)) {
            return List.of(new MailboxSyncAttemptResponse(a.getLastSyncAt(), true, "Sync OK"));
        }
        Instant at = a.getLastErrorAt() != null ? a.getLastErrorAt() : a.getLastSyncAt();
        String msg = a.getLastError() != null ? a.getLastError() : s;
        return List.of(new MailboxSyncAttemptResponse(at, false, msg));
    }
}
