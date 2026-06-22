package com.postwerk.service.impl;

import com.postwerk.dto.admin.StaffCandidateResponse;
import com.postwerk.dto.admin.StaffKpisResponse;
import com.postwerk.dto.admin.StaffMemberResponse;
import com.postwerk.dto.admin.StaffRoleInfoResponse;
import com.postwerk.dto.admin.StaffRolesMatrixResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.AuditAction;
import com.postwerk.model.User;
import com.postwerk.model.enums.StaffPermission;
import com.postwerk.model.enums.StaffRole;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AdminService;
import com.postwerk.service.AdminStaffService;
import com.postwerk.service.AuditService;
import com.postwerk.util.EnumUtil;
import com.postwerk.util.InMemoryPage;
import com.postwerk.util.SafeStrings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdminStaffService}. The roster is filtered/sorted/paginated in-memory (staff are
 * low-volume); the role→capability matrix comes straight from {@link StaffRole#permissions()} so it
 * can never drift from enforcement. Mutations delegate the actual role change to
 * {@link AdminService#updateStaffRole} (which rejects self-change + stamps {@code staffRoleSince})
 * and then write an audit entry.
 *
 * @since 1.0
 */
@Service
public class AdminStaffServiceImpl implements AdminStaffService {

    /** Display + sort order, privileged-first (mirrors the design's role ranking). */
    private static final List<StaffRole> ROLE_ORDER = List.of(
            StaffRole.SUPER_ADMIN, StaffRole.ADMIN, StaffRole.BILLING,
            StaffRole.MODERATOR, StaffRole.SUPPORT, StaffRole.AUDITOR);
    private static final List<StaffRole> PRIVILEGED = List.of(
            StaffRole.SUPER_ADMIN, StaffRole.ADMIN, StaffRole.BILLING, StaffRole.MODERATOR);

    private final UserRepository userRepository;
    private final AdminService adminService;
    private final AuditService auditService;

    public AdminStaffServiceImpl(UserRepository userRepository, AdminService adminService, AuditService auditService) {
        this.userRepository = userRepository;
        this.adminService = adminService;
        this.auditService = auditService;
    }

    private static boolean privileged(StaffRole r) { return PRIVILEGED.contains(r); }
    private static String tier(StaffRole r) { return privileged(r) ? "PRIVILEGED" : "READ_ONLY"; }
    private static int rank(StaffRole r) { int i = ROLE_ORDER.indexOf(r); return i < 0 ? 99 : i; }

    // ── Roster ──────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public Page<StaffMemberResponse> listRoster(String search, String role, String tier, String sort,
                                                String actorEmail, Pageable pageable) {
        String q = search == null ? "" : search.trim().toLowerCase();
        List<StaffMemberResponse> rows = userRepository.findByStaffRoleIsNotNull().stream()
                .map(u -> toMember(u, actorEmail))
                .filter(m -> {
                    if (!q.isEmpty() && !(SafeStrings.containsIgnoreCase(m.name(), q)
                            || SafeStrings.containsIgnoreCase(m.email(), q)
                            || SafeStrings.containsIgnoreCase(m.role(), q))) return false;
                    if (role != null && !role.isBlank() && !role.equalsIgnoreCase(m.role())) return false;
                    if (tier != null && !tier.isBlank() && !tier.equalsIgnoreCase(m.tier())) return false;
                    return true;
                })
                .sorted(comparator(sort))
                .collect(Collectors.toList());
        return InMemoryPage.of(rows, pageable);
    }

    private Comparator<StaffMemberResponse> comparator(String sort) {
        return switch (sort == null ? "role" : sort) {
            case "lastActive" -> Comparator.comparing(StaffMemberResponse::lastActiveAt, Comparator.nullsLast(Comparator.reverseOrder()));
            case "staffSince" -> Comparator.comparing(StaffMemberResponse::staffSince, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparingInt((StaffMemberResponse m) -> roleRank(m.role())).thenComparing(StaffMemberResponse::name);
        };
    }
    private static int roleRank(String role) { try { return rank(StaffRole.valueOf(role)); } catch (Exception e) { return 99; } }

    // ── KPIs ──────────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public StaffKpisResponse kpis() {
        List<User> staff = userRepository.findByStaffRoleIsNotNull();
        Instant floor = Instant.now().minus(30, ChronoUnit.DAYS);
        long total = staff.size();
        long superAdmins = staff.stream().filter(u -> u.getStaffRole() == StaffRole.SUPER_ADMIN).count();
        long priv = staff.stream().filter(u -> privileged(u.getStaffRole())).count();
        long readOnly = total - priv;
        long added30d = staff.stream().filter(u -> u.getStaffRoleSince() != null && !u.getStaffRoleSince().isBefore(floor)).count();
        return new StaffKpisResponse(total, superAdmins, priv, readOnly, added30d);
    }

    // ── Roles matrix ────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public StaffRolesMatrixResponse rolesMatrix() {
        List<StaffRoleInfoResponse> roles = ROLE_ORDER.stream()
                .map(r -> new StaffRoleInfoResponse(r.name(), tier(r), privileged(r),
                        r.permissions().stream().map(Enum::name).sorted().toList()))
                .toList();
        List<String> all = Arrays.stream(StaffPermission.values()).map(Enum::name).toList();
        return new StaffRolesMatrixResponse(roles, all);
    }

    // ── Candidates ────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<StaffCandidateResponse> candidates(String search) {
        return userRepository.searchNonStaff(search == null ? "" : search.trim(), 8).stream()
                .map(u -> new StaffCandidateResponse(u.getId(), u.getFullName(), u.getEmail()))
                .toList();
    }

    // ── Mutations ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public StaffMemberResponse setRole(UUID userId, String role, String actorEmail, String ip) {
        StaffRole parsed = EnumUtil.parseOrThrow(StaffRole.class, role, "staff role");
        StaffRole old = reload(userId).getStaffRole(); // null = first grant
        // delegates self-change rejection + staffRoleSince stamping
        adminService.updateStaffRole(userId, parsed.name(), actorEmail);
        AuditAction action = old == null ? AuditAction.STAFF_ROLE_GRANTED : AuditAction.STAFF_ROLE_CHANGED;
        String detail = old == null ? "Granted staff role " + parsed.name()
                : "Changed staff role " + old.name() + " → " + parsed.name();
        audit(actorEmail, action, userId, detail, ip);
        return toMember(reload(userId), actorEmail);
    }

    @Override
    @Transactional
    public StaffMemberResponse revoke(UUID userId, String actorEmail, String ip) {
        StaffRole old = reload(userId).getStaffRole();
        adminService.updateStaffRole(userId, null, actorEmail);
        audit(actorEmail, AuditAction.STAFF_ROLE_REVOKED,
                userId, "Revoked staff access" + (old != null ? " (was " + old.name() + ")" : ""), ip);
        return toMember(reload(userId), actorEmail);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private User reload(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }

    private void audit(String actorEmail, AuditAction action, UUID targetUserId, String detail, String ip) {
        UUID actorId = userRepository.findByEmail(actorEmail).map(User::getId).orElse(null);
        auditService.log(actorId, action, detail + " · user " + targetUserId, ip);
    }

    private StaffMemberResponse toMember(User u, String actorEmail) {
        StaffRole r = u.getStaffRole();
        int caps = r == null ? 0 : r.permissions().size();
        return new StaffMemberResponse(u.getId(), u.getFullName(), u.getEmail(),
                r != null ? r.name() : null, r != null ? tier(r) : null, caps,
                u.getLastLoginAt(), u.getStaffRoleSince(),
                u.getEmail() != null && u.getEmail().equalsIgnoreCase(actorEmail));
    }

}
