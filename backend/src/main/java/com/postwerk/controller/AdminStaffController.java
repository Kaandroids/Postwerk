package com.postwerk.controller;

import com.postwerk.dto.admin.SetStaffRoleRequest;
import com.postwerk.dto.admin.StaffCandidateResponse;
import com.postwerk.dto.admin.StaffKpisResponse;
import com.postwerk.dto.admin.StaffMemberResponse;
import com.postwerk.dto.admin.StaffRolesMatrixResponse;
import com.postwerk.service.AdminStaffService;
import com.postwerk.util.IpResolverUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform-staff Staff &amp; Roles console. Path-gated to {@code ROLE_STAFF} by {@code SecurityConfig};
 * view + every mutation gate on {@code STAFF_MANAGE} (Super Admin only). The actual role change
 * reuses {@code AdminService.updateStaffRole} (which rejects self-change); this surface adds the
 * roster, KPIs, the role→capability matrix and grant-access candidates.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasAuthority('STAFF_MANAGE')")
@Tag(name = "Admin — Staff & Roles", description = "Platform staff roster + role management")
public class AdminStaffController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminStaffService service;

    public AdminStaffController(AdminStaffService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page<StaffMemberResponse>> roster(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(service.listRoster(search, role, tier, sort, principal.getUsername(),
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @GetMapping("/kpis")
    public ResponseEntity<StaffKpisResponse> kpis() {
        return ResponseEntity.ok(service.kpis());
    }

    @GetMapping("/roles")
    public ResponseEntity<StaffRolesMatrixResponse> roles() {
        return ResponseEntity.ok(service.rolesMatrix());
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<StaffCandidateResponse>> candidates(@RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(service.candidates(search));
    }

    /** Grant or change a staff member's role. */
    @PostMapping("/{userId}")
    public ResponseEntity<StaffMemberResponse> setRole(@PathVariable UUID userId,
                                                       @Valid @RequestBody SetStaffRoleRequest req,
                                                       @AuthenticationPrincipal UserDetails principal,
                                                       HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.setRole(userId, req.role(), principal.getUsername(), IpResolverUtil.extractIp(httpReq)));
    }

    /** Revoke all staff access. */
    @DeleteMapping("/{userId}")
    public ResponseEntity<StaffMemberResponse> revoke(@PathVariable UUID userId,
                                                      @AuthenticationPrincipal UserDetails principal,
                                                      HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.revoke(userId, principal.getUsername(), IpResolverUtil.extractIp(httpReq)));
    }
}
