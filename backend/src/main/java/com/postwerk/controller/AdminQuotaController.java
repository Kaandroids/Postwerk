package com.postwerk.controller;

import com.postwerk.dto.admin.QuotaKpisResponse;
import com.postwerk.dto.admin.QuotaOverrideRequest;
import com.postwerk.dto.admin.QuotaOverrideResponse;
import com.postwerk.service.AdminQuotaService;
import com.postwerk.service.UserIdResolverService;
import com.postwerk.util.IpResolverUtil;
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

import java.util.UUID;

/**
 * Platform-staff management of AI quota overrides (admin "Quota Overrides" page). Path-gated to
 * {@code ROLE_STAFF} by {@code SecurityConfig}; reads require {@code AI_USAGE_VIEW}, mutations require
 * {@code QUOTA_OVERRIDE}.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/quota-overrides")
@Tag(name = "Admin — Quota Overrides", description = "Platform staff management of AI quota overrides")
public class AdminQuotaController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminQuotaService service;
    private final UserIdResolverService userIdResolver;

    public AdminQuotaController(AdminQuotaService service, UserIdResolverService userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AI_USAGE_VIEW')")
    public ResponseEntity<Page<QuotaOverrideResponse>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String expiry,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.list(search, targetType, kind, status, expiry,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by("createdAt").descending())));
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('AI_USAGE_VIEW')")
    public ResponseEntity<QuotaKpisResponse> kpis() {
        return ResponseEntity.ok(service.kpis());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('QUOTA_OVERRIDE')")
    public ResponseEntity<QuotaOverrideResponse> create(@Valid @RequestBody QuotaOverrideRequest request,
                                                        @AuthenticationPrincipal UserDetails principal,
                                                        HttpServletRequest httpRequest) {
        UUID staffUserId = userIdResolver.resolve(principal);
        return ResponseEntity.status(201)
                .body(service.create(request, staffUserId, IpResolverUtil.extractIp(httpRequest)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('QUOTA_OVERRIDE')")
    public ResponseEntity<QuotaOverrideResponse> update(@PathVariable UUID id,
                                                        @Valid @RequestBody QuotaOverrideRequest request,
                                                        @AuthenticationPrincipal UserDetails principal,
                                                        HttpServletRequest httpRequest) {
        UUID staffUserId = userIdResolver.resolve(principal);
        return ResponseEntity.ok(service.update(id, request, staffUserId, IpResolverUtil.extractIp(httpRequest)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('QUOTA_OVERRIDE')")
    public ResponseEntity<Void> revoke(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserDetails principal,
                                       HttpServletRequest httpRequest) {
        UUID staffUserId = userIdResolver.resolve(principal);
        service.revoke(id, staffUserId, IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
