package com.postwerk.controller;

import com.postwerk.dto.admin.*;
import com.postwerk.model.AuditAction;
import com.postwerk.service.AdminService;
import com.postwerk.service.AuditService;
import com.postwerk.service.PricingService;
import com.postwerk.service.UserIdResolverService;
import com.postwerk.util.IpResolverUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for platform administration operations.
 * Delegates to {@link AdminService} for user management, analytics, audit logs, and plan CRUD.
 *
 * <p>Access is gated in two layers: {@code SecurityConfig} requires {@code ROLE_STAFF} for the
 * whole {@code /api/v1/admin/**} surface, and each method below additionally requires a discrete
 * {@link com.postwerk.model.enums.StaffPermission} via {@code @PreAuthorize} — so e.g. a SUPPORT
 * staffer can read users but not mutate plans.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Platform administration endpoints (platform staff only)")
public class AdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminService adminService;
    private final UserIdResolverService userIdResolver;
    private final AuditService auditService;
    private final PricingService pricingService;

    public AdminController(AdminService adminService, UserIdResolverService userIdResolver,
                           AuditService auditService, PricingService pricingService) {
        this.adminService = adminService;
        this.userIdResolver = userIdResolver;
        this.auditService = auditService;
        this.pricingService = pricingService;
    }

    /**
     * Records an audit entry for a mutating admin action. These core user/plan endpoints predate the
     * actor-threading convention used by the newer admin services, so the actor + IP are resolved and
     * the entry written here at the controller (the action still produces an accountable record).
     */
    private void audit(UserDetails principal, HttpServletRequest request, AuditAction action, String detail) {
        auditService.log(userIdResolver.resolve(principal), action, detail, request);
    }

    // ─── Current Staff Identity ─────────────────────────────────────

    /** The caller's own staff role + effective capabilities, for the UI to gate admin features on.
     *  Reachable by ANY authenticated user (allowlisted in SecurityConfig before the /admin/** STAFF rule):
     *  normal users get a response with {@code staffRole = null} + empty permissions, so the UI hides admin. */
    @GetMapping("/me")
    public ResponseEntity<StaffIdentityResponse> me(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(adminService.getStaffIdentity(principal.getUsername()));
    }

    // ─── Dashboard Stats ────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('PLATFORM_DASHBOARD_VIEW')")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    // ─── User Management ────────────────────────────────────────────

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<Page<AdminUserResponse>> getUsers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plan,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getUsers(search, role, status, plan,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by("created_at").descending())));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUser(id));
    }

    @GetMapping("/users/{id}/organizations")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<List<AdminUserOrgResponse>> getUserOrganizations(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUserOrganizations(id));
    }

    @GetMapping("/users/{id}/mailboxes")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<List<AdminMailboxResponse>> getUserMailboxes(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUserMailboxes(id));
    }

    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<AdminUserResponse> updateRole(@PathVariable UUID id,
                                                         @Valid @RequestBody RoleUpdateRequest request,
                                                         @AuthenticationPrincipal UserDetails principal,
                                                         HttpServletRequest httpRequest) {
        AdminUserResponse response = adminService.updateRole(id, request.role(), principal.getUsername());
        audit(principal, httpRequest, AuditAction.USER_ROLE_CHANGED,
                "Changed role of user " + id + " to " + response.role());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/users/{id}/disable")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<Void> disableUser(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserDetails principal,
                                            HttpServletRequest httpRequest) {
        adminService.disableUser(id);
        audit(principal, httpRequest, AuditAction.USER_DISABLED, "Disabled user " + id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/staff-role")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public ResponseEntity<AdminUserResponse> updateStaffRole(@PathVariable UUID id,
                                                             @Valid @RequestBody StaffRoleUpdateRequest request,
                                                             @AuthenticationPrincipal UserDetails principal,
                                                             HttpServletRequest httpRequest) {
        // Read the prior role so we can record GRANTED / CHANGED / REVOKED — granting platform-staff
        // access is the single most privilege-sensitive action and must leave a precise trail.
        String before = adminService.getUser(id).staffRole();
        AdminUserResponse response = adminService.updateStaffRole(id, request.staffRole(), principal.getUsername());
        String after = response.staffRole();
        AuditAction action = after == null ? AuditAction.STAFF_ROLE_REVOKED
                : before == null ? AuditAction.STAFF_ROLE_GRANTED : AuditAction.STAFF_ROLE_CHANGED;
        audit(principal, httpRequest, action,
                "Staff role of user " + id + (after == null ? " revoked" : " set to " + after));
        return ResponseEntity.ok(response);
    }

    // ─── Users support tooling: staff notes, credential reset, sessions ──

    @GetMapping("/users/{id}/notes")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<List<StaffNoteResponse>> getUserNotes(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.listUserNotes(id));
    }

    /** Writing an internal note needs only view access; the current staffer is recorded as author. */
    @PostMapping("/users/{id}/notes")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<StaffNoteResponse> addUserNote(@PathVariable UUID id,
                                                         @Valid @RequestBody StaffNoteRequest request,
                                                         @AuthenticationPrincipal UserDetails principal) {
        UUID authorId = userIdResolver.resolve(principal);
        return ResponseEntity.status(201).body(adminService.addUserNote(id, authorId, request.body()));
    }

    /**
     * Deletes an internal staff note. Path gate is only USER_VIEW; the service then enforces
     * author-or-USER_MANAGE (else 403). We pass whether the caller holds USER_MANAGE.
     */
    @DeleteMapping("/users/{userId}/notes/{noteId}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<Void> deleteUserNote(@PathVariable UUID userId,
                                               @PathVariable UUID noteId,
                                               @AuthenticationPrincipal UserDetails principal,
                                               Authentication authentication) {
        UUID requesterId = userIdResolver.resolve(principal);
        boolean hasUserManage = hasAuthority(authentication, "USER_MANAGE");
        adminService.deleteUserNote(noteId, requesterId, hasUserManage);
        return ResponseEntity.noContent().build();
    }

    /** Triggers the normal password-reset flow for the target user (no password is set/returned). */
    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_CREDENTIAL_RESET')")
    public ResponseEntity<Void> forcePasswordReset(@PathVariable UUID id,
                                                   @AuthenticationPrincipal UserDetails principal,
                                                   HttpServletRequest httpRequest) {
        adminService.forcePasswordReset(id, IpResolverUtil.extractIp(httpRequest));
        audit(principal, httpRequest, AuditAction.USER_PASSWORD_RESET_FORCED,
                "Forced password reset for user " + id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{id}/sessions")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<AdminUserSessionsResponse> getUserSessions(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUserSessions(id));
    }

    /** Revokes all of the target user's sessions (force logout). Returns the new count (0). */
    @PostMapping("/users/{id}/revoke-sessions")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<AdminUserSessionsResponse> revokeUserSessions(@PathVariable UUID id,
                                                                        @AuthenticationPrincipal UserDetails principal,
                                                                        HttpServletRequest httpRequest) {
        AdminUserSessionsResponse response = adminService.revokeUserSessions(id);
        audit(principal, httpRequest, AuditAction.USER_SESSIONS_REVOKED, "Revoked all sessions for user " + id);
        return ResponseEntity.ok(response);
    }

    /** Whether the authenticated caller holds the given granted authority (staff permission). */
    private static boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null) return false;
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) return true;
        }
        return false;
    }

    // ─── AI Usage ───────────────────────────────────────────────────

    @GetMapping("/ai-usage")
    @PreAuthorize("hasAuthority('AI_USAGE_VIEW')")
    public ResponseEntity<AiUsageStatsResponse> getAiUsageStats() {
        return ResponseEntity.ok(adminService.getAiUsageStats());
    }

    @GetMapping("/ai-usage/by-user")
    @PreAuthorize("hasAuthority('AI_USAGE_VIEW')")
    public ResponseEntity<List<AiUsageByUserResponse>> getAiUsageByUser() {
        return ResponseEntity.ok(adminService.getAiUsageByUser());
    }

    @GetMapping("/ai-usage/timeline")
    @PreAuthorize("hasAuthority('AI_USAGE_VIEW')")
    public ResponseEntity<List<TimelineDataPoint>> getAiUsageTimeline(
            @RequestParam(defaultValue = "daily") String period) {
        return ResponseEntity.ok(adminService.getAiUsageTimeline(period));
    }

    // ─── Automation Stats ───────────────────────────────────────────

    @GetMapping("/automations/stats")
    @PreAuthorize("hasAuthority('AUTOMATION_OVERSIGHT_VIEW')")
    public ResponseEntity<AutomationStatsResponse> getAutomationStats() {
        return ResponseEntity.ok(adminService.getAutomationStats());
    }

    @GetMapping("/automations/executions")
    @PreAuthorize("hasAuthority('AUTOMATION_OVERSIGHT_VIEW')")
    public ResponseEntity<Page<AdminService.AutomationExecutionAdminResponse>> getExecutions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getAutomationExecutions(PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    // ─── Audit Log ──────────────────────────────────────────────────

    @GetMapping("/audit-log")
    @PreAuthorize("hasAuthority('AUDIT_LOG_VIEW')")
    public ResponseEntity<Page<AdminAuditLogResponse>> getAuditLog(
            @RequestParam(required = false) UUID user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getAuditLog(user, action, organizationId,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @GetMapping("/audit-log/export")
    @PreAuthorize("hasAuthority('AUDIT_LOG_VIEW')")
    public ResponseEntity<byte[]> exportAuditLog(
            @RequestParam(required = false) UUID user,
            @RequestParam(required = false) String action) {
        byte[] csv = adminService.exportAuditLogCsv(user, action);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-log.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    // ─── Plans ──────────────────────────────────────────────────────

    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('PLAN_VIEW')")
    public ResponseEntity<List<PlanResponse>> getPlans() {
        return ResponseEntity.ok(adminService.getPlans());
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('PLAN_MANAGE')")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody PlanRequest request,
                                                   @AuthenticationPrincipal UserDetails principal,
                                                   HttpServletRequest httpRequest) {
        PlanResponse response = adminService.createPlan(request);
        audit(principal, httpRequest, AuditAction.PLAN_CREATED, "Created plan " + response.name());
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('PLAN_MANAGE')")
    public ResponseEntity<PlanResponse> updatePlan(@PathVariable UUID id,
                                                    @Valid @RequestBody PlanRequest request,
                                                    @AuthenticationPrincipal UserDetails principal,
                                                    HttpServletRequest httpRequest) {
        PlanResponse response = adminService.updatePlan(id, request);
        audit(principal, httpRequest, AuditAction.PLAN_UPDATED, "Updated plan " + id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('PLAN_MANAGE')")
    public ResponseEntity<Void> deletePlan(@PathVariable UUID id,
                                           @AuthenticationPrincipal UserDetails principal,
                                           HttpServletRequest httpRequest) {
        adminService.deletePlan(id);
        audit(principal, httpRequest, AuditAction.PLAN_DELETED, "Deleted plan " + id);
        return ResponseEntity.noContent().build();
    }

    // ─── AI Model Pricing ───────────────────────────────────────────
    // Per-model token rates (USD/M). Editing here takes effect immediately (no restart); unknown
    // models fall back to the application.yml rates. Gated with the same PLAN_VIEW/PLAN_MANAGE perms.

    @GetMapping("/pricing/models")
    @PreAuthorize("hasAuthority('PLAN_VIEW')")
    public ResponseEntity<List<ModelPricingResponse>> getModelPricing() {
        return ResponseEntity.ok(pricingService.listModels());
    }

    @PostMapping("/pricing/models")
    @PreAuthorize("hasAuthority('PLAN_MANAGE')")
    public ResponseEntity<ModelPricingResponse> createModelPricing(@Valid @RequestBody ModelPricingRequest request,
                                                                   @AuthenticationPrincipal UserDetails principal,
                                                                   HttpServletRequest httpRequest) {
        ModelPricingResponse response = pricingService.createModel(request);
        audit(principal, httpRequest, AuditAction.PRICING_UPDATED, "Created pricing for " + response.model());
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/pricing/models/{id}")
    @PreAuthorize("hasAuthority('PLAN_MANAGE')")
    public ResponseEntity<ModelPricingResponse> updateModelPricing(@PathVariable UUID id,
                                                                   @Valid @RequestBody ModelPricingRequest request,
                                                                   @AuthenticationPrincipal UserDetails principal,
                                                                   HttpServletRequest httpRequest) {
        ModelPricingResponse response = pricingService.updateModel(id, request);
        audit(principal, httpRequest, AuditAction.PRICING_UPDATED, "Updated pricing for " + response.model());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/pricing/models/{id}")
    @PreAuthorize("hasAuthority('PLAN_MANAGE')")
    public ResponseEntity<Void> deleteModelPricing(@PathVariable UUID id,
                                                   @AuthenticationPrincipal UserDetails principal,
                                                   HttpServletRequest httpRequest) {
        pricingService.deleteModel(id);
        audit(principal, httpRequest, AuditAction.PRICING_UPDATED, "Deleted pricing " + id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/plan")
    @PreAuthorize("hasAuthority('PLAN_MANAGE')")
    public ResponseEntity<AdminUserResponse> assignPlan(@PathVariable UUID id,
                                                         @Valid @RequestBody AssignPlanRequest request,
                                                         @AuthenticationPrincipal UserDetails principal,
                                                         HttpServletRequest httpRequest) {
        AdminUserResponse response = adminService.assignPlan(id, request.planId());
        audit(principal, httpRequest, AuditAction.PLAN_ASSIGNED,
                "Assigned plan " + request.planId() + " to user " + id);
        return ResponseEntity.ok(response);
    }
}
