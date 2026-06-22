package com.postwerk.controller;

import com.postwerk.dto.admin.AdminDataRequestDetailResponse;
import com.postwerk.dto.admin.AdminDataRequestResponse;
import com.postwerk.dto.admin.CreateDataRequestRequest;
import com.postwerk.dto.admin.GdprKpisResponse;
import com.postwerk.dto.admin.RejectDataRequestRequest;
import com.postwerk.dto.admin.RetentionPostureResponse;
import com.postwerk.service.AdminGdprService;
import com.postwerk.service.UserIdResolverService;
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

import java.util.UUID;

/**
 * Platform-staff GDPR / Data Requests (DSAR) console. Path-gated to {@code ROLE_STAFF} by
 * {@code SecurityConfig}; reads require {@code COMPLIANCE_VIEW}, every mutation requires
 * {@code COMPLIANCE_MANAGE}. Erasure is irreversible.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/gdpr")
@Tag(name = "Admin — GDPR / Data Requests", description = "Platform staff handling of data-subject access requests")
public class AdminGdprController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminGdprService service;
    private final UserIdResolverService userIdResolver;

    public AdminGdprController(AdminGdprService service, UserIdResolverService userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    private UUID actor(UserDetails principal) { return userIdResolver.resolve(principal); }

    // ── Reads (COMPLIANCE_VIEW) ───────────────────────────────────────────────────
    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('COMPLIANCE_VIEW')")
    public ResponseEntity<Page<AdminDataRequestResponse>> requests(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String deadline,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.listRequests(search, type, status, deadline, sort, dir,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('COMPLIANCE_VIEW')")
    public ResponseEntity<GdprKpisResponse> kpis() {
        return ResponseEntity.ok(service.kpis());
    }

    @GetMapping("/retention")
    @PreAuthorize("hasAuthority('COMPLIANCE_VIEW')")
    public ResponseEntity<RetentionPostureResponse> retention() {
        return ResponseEntity.ok(service.retention());
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize("hasAuthority('COMPLIANCE_VIEW')")
    public ResponseEntity<AdminDataRequestDetailResponse> getRequest(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getRequest(id));
    }

    // ── Mutations (COMPLIANCE_MANAGE) ─────────────────────────────────────────────
    @PostMapping("/requests")
    @PreAuthorize("hasAuthority('COMPLIANCE_MANAGE')")
    public ResponseEntity<AdminDataRequestResponse> create(@Valid @RequestBody CreateDataRequestRequest req,
                                                           @AuthenticationPrincipal UserDetails principal,
                                                           HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.create(req, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/requests/{id}/export")
    @PreAuthorize("hasAuthority('COMPLIANCE_MANAGE')")
    public ResponseEntity<AdminDataRequestResponse> runExport(@PathVariable UUID id,
                                                              @AuthenticationPrincipal UserDetails principal,
                                                              HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.runExport(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/requests/{id}/erase")
    @PreAuthorize("hasAuthority('COMPLIANCE_MANAGE')")
    public ResponseEntity<AdminDataRequestResponse> executeErasure(@PathVariable UUID id,
                                                                   @AuthenticationPrincipal UserDetails principal,
                                                                   HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.executeErasure(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasAuthority('COMPLIANCE_MANAGE')")
    public ResponseEntity<AdminDataRequestResponse> reject(@PathVariable UUID id,
                                                           @Valid @RequestBody RejectDataRequestRequest req,
                                                           @AuthenticationPrincipal UserDetails principal,
                                                           HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.reject(id, req.reason(), actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/requests/{id}/complete")
    @PreAuthorize("hasAuthority('COMPLIANCE_MANAGE')")
    public ResponseEntity<AdminDataRequestResponse> markComplete(@PathVariable UUID id,
                                                                 @AuthenticationPrincipal UserDetails principal,
                                                                 HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.markComplete(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }
}
