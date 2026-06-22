package com.postwerk.controller;

import com.postwerk.dto.AuditLogResponse;
import com.postwerk.model.AuditAction;
import com.postwerk.model.AuditLog;
import com.postwerk.model.User;
import com.postwerk.repository.AuditLogRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.OrgContext;
import com.postwerk.util.EnumUtil;
import com.postwerk.util.UuidUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for querying the organization-scoped audit log.
 *
 * <p>Entries are recorded asynchronously by {@link com.postwerk.service.AuditService} and stamped with the
 * organization the action happened in. Org owners and admins see the whole active org's trail — optionally
 * narrowed to a single member; everyone else (members, viewers) sees only their own actions within that org.
 * Strictly scoped to the active org, so tenants never see each other's trails.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit Logs", description = "Organization-scoped security and activity audit trail")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogController(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns paginated audit entries for the active org, optionally filtered by action type and member.
     * Org-wide for owners/admins; restricted to the caller's own entries for everyone else.
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> list(
            OrgContext ctx,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String member,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        UUID orgId = ctx.organizationId();
        AuditAction auditAction = EnumUtil.parseOrDefault(AuditAction.class, action, null);

        // Owner/Admin => may see the whole org (and narrow to a member); everyone else only their own entries.
        boolean orgWide = ctx.isAdminOrAbove();
        UUID memberFilter = orgWide
                ? (member != null && !member.isBlank() ? UuidUtil.parseOrThrow(member, "member id") : null)
                : ctx.userId();

        Page<AuditLog> page;
        if (memberFilter != null) {
            page = auditAction != null
                    ? auditLogRepository.findByOrganizationIdAndUserIdAndActionOrderByCreatedAtDesc(orgId, memberFilter, auditAction, pageable)
                    : auditLogRepository.findByOrganizationIdAndUserIdOrderByCreatedAtDesc(orgId, memberFilter, pageable);
        } else {
            page = auditAction != null
                    ? auditLogRepository.findByOrganizationIdAndActionOrderByCreatedAtDesc(orgId, auditAction, pageable)
                    : auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable);
        }

        Map<UUID, String> names = resolveActorNames(page.getContent());
        return ResponseEntity.ok(page.map(entry -> toResponse(entry, names)));
    }

    /** Batch-resolves actor (user) display names for the page in a single query (avoids per-row N+1). */
    private Map<UUID, String> resolveActorNames(List<AuditLog> entries) {
        List<UUID> ids = entries.stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, AuditLogController::displayName, (a, b) -> a));
    }

    private static String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getEmail();
    }

    private AuditLogResponse toResponse(AuditLog log, Map<UUID, String> names) {
        return new AuditLogResponse(
                log.getId(),
                log.getUserId(),
                log.getUserId() != null ? names.get(log.getUserId()) : null,
                log.getAction().name(),
                log.getDetail(),
                log.getIpAddress(),
                log.getCreatedAt()
        );
    }
}
