package com.postwerk.controller;

import com.postwerk.dto.admin.AdminAutomationSummaryResponse;
import com.postwerk.dto.admin.AdminMailboxResponse;
import com.postwerk.dto.admin.AdminOrgDetailResponse;
import com.postwerk.dto.admin.AdminOrgResponse;
import com.postwerk.dto.admin.SuspendOrganizationRequest;
import com.postwerk.dto.admin.TransferOwnershipRequest;
import com.postwerk.model.AuditAction;
import com.postwerk.service.AdminOrganizationService;
import com.postwerk.service.AuditService;
import com.postwerk.service.UserIdResolverService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform-staff oversight of customer organizations. Path-gated to {@code ROLE_STAFF} by
 * {@code SecurityConfig}; each method additionally requires a discrete {@code ORG_*} capability.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@Tag(name = "Admin — Organizations", description = "Platform staff oversight of customer organizations")
public class AdminOrganizationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminOrganizationService service;
    private final UserIdResolverService userIdResolver;
    private final AuditService auditService;

    public AdminOrganizationController(AdminOrganizationService service,
                                       UserIdResolverService userIdResolver,
                                       AuditService auditService) {
        this.service = service;
        this.userIdResolver = userIdResolver;
        this.auditService = auditService;
    }

    /** Records an accountable audit entry (actor + IP) for a destructive org action. */
    private void audit(UserDetails principal, HttpServletRequest request, AuditAction action, String detail) {
        auditService.log(userIdResolver.resolve(principal), action, detail, request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORG_VIEW')")
    public ResponseEntity<Page<AdminOrgResponse>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Boolean personal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.listOrganizations(search, personal,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by("createdAt").descending())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_VIEW')")
    public ResponseEntity<AdminOrgDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getOrganization(id));
    }

    @GetMapping("/{id}/automations")
    @PreAuthorize("hasAuthority('ORG_VIEW')")
    public ResponseEntity<List<AdminAutomationSummaryResponse>> getAutomations(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getOrganizationAutomations(id));
    }

    @GetMapping("/{id}/mailboxes")
    @PreAuthorize("hasAuthority('ORG_VIEW')")
    public ResponseEntity<List<AdminMailboxResponse>> getMailboxes(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getOrganizationMailboxes(id));
    }

    @PostMapping("/{id}/transfer-ownership")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ResponseEntity<AdminOrgDetailResponse> transferOwnership(@PathVariable UUID id,
                                                                    @Valid @RequestBody TransferOwnershipRequest request,
                                                                    @AuthenticationPrincipal UserDetails principal,
                                                                    HttpServletRequest httpRequest) {
        AdminOrgDetailResponse response = service.transferOwnership(id, request.newOwnerUserId());
        audit(principal, httpRequest, AuditAction.ORG_OWNERSHIP_TRANSFERRED,
                "Transferred ownership of org " + id + " to user " + request.newOwnerUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ResponseEntity<AdminOrgDetailResponse> suspend(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) SuspendOrganizationRequest request,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpRequest) {
        AdminOrgDetailResponse response = service.suspendOrganization(id, request != null ? request.reason() : null);
        audit(principal, httpRequest, AuditAction.ORG_SUSPENDED, "Suspended org " + id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ResponseEntity<AdminOrgDetailResponse> activate(@PathVariable UUID id,
                                                           @AuthenticationPrincipal UserDetails principal,
                                                           HttpServletRequest httpRequest) {
        AdminOrgDetailResponse response = service.activateOrganization(id);
        audit(principal, httpRequest, AuditAction.ORG_ACTIVATED, "Reactivated org " + id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserDetails principal,
                                       HttpServletRequest httpRequest) {
        service.deleteOrganization(id);
        audit(principal, httpRequest, AuditAction.ORG_DELETED, "Deleted org " + id);
        return ResponseEntity.noContent().build();
    }
}
