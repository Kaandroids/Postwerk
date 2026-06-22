package com.postwerk.controller;

import com.postwerk.dto.admin.AdminSubscriptionDetailResponse;
import com.postwerk.dto.admin.AdminSubscriptionResponse;
import com.postwerk.dto.admin.ChangePlanRequest;
import com.postwerk.dto.admin.PlanHistoryEntryResponse;
import com.postwerk.dto.admin.SubscriptionKpisResponse;
import com.postwerk.service.AdminSubscriptionService;
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

import java.util.List;
import java.util.UUID;

/**
 * Platform-staff Plans &amp; Subscriptions — every org's plan assignment + derived MRR + metered AI
 * usage. Path-gated to {@code ROLE_STAFF} by {@code SecurityConfig}; reads need {@code PLAN_VIEW} or
 * {@code BILLING_VIEW}, plan changes need {@code PLAN_MANAGE}. (Suspend/activate is the existing
 * organizations API; grant-credit is the quota-overrides API.)
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/subscriptions")
@Tag(name = "Admin — Subscriptions", description = "Platform staff plans & subscriptions / billing oversight")
public class AdminSubscriptionController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String READ = "hasAuthority('PLAN_VIEW') or hasAuthority('BILLING_VIEW')";

    private final AdminSubscriptionService service;
    private final UserIdResolverService userIdResolver;

    public AdminSubscriptionController(AdminSubscriptionService service, UserIdResolverService userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<Page<AdminSubscriptionResponse>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String usage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.listSubscriptions(search, plan, status, usage,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @GetMapping("/kpis")
    @PreAuthorize(READ)
    public ResponseEntity<SubscriptionKpisResponse> kpis() {
        return ResponseEntity.ok(service.kpis());
    }

    @GetMapping("/{orgId}")
    @PreAuthorize(READ)
    public ResponseEntity<AdminSubscriptionDetailResponse> get(@PathVariable UUID orgId) {
        return ResponseEntity.ok(service.getSubscription(orgId));
    }

    @GetMapping("/{orgId}/plan-history")
    @PreAuthorize(READ)
    public ResponseEntity<List<PlanHistoryEntryResponse>> planHistory(@PathVariable UUID orgId) {
        return ResponseEntity.ok(service.planHistory(orgId));
    }

    @PatchMapping("/{orgId}/plan")
    @PreAuthorize("hasAuthority('PLAN_MANAGE')")
    public ResponseEntity<AdminSubscriptionDetailResponse> changePlan(@PathVariable UUID orgId,
                                                                      @Valid @RequestBody ChangePlanRequest request,
                                                                      @AuthenticationPrincipal UserDetails principal,
                                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.changePlan(orgId, request.planId(), request.reason(),
                userIdResolver.resolve(principal), IpResolverUtil.extractIp(httpRequest)));
    }
}
