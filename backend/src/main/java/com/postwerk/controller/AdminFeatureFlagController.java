package com.postwerk.controller;

import com.postwerk.dto.admin.AdminFlagDetailResponse;
import com.postwerk.dto.admin.AdminFlagResponse;
import com.postwerk.dto.admin.CreateFlagRequest;
import com.postwerk.dto.admin.FeatureFlagKpisResponse;
import com.postwerk.dto.admin.UpdateFlagRequest;
import com.postwerk.service.AdminFeatureFlagService;
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
 * Platform-staff Feature Flags console. Path-gated to {@code ROLE_STAFF} by {@code SecurityConfig};
 * view + every mutation gate on {@code FEATURE_FLAG_MANAGE} (the single capability for this surface).
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/feature-flags")
@PreAuthorize("hasAuthority('FEATURE_FLAG_MANAGE')")
@Tag(name = "Admin — Feature Flags", description = "Platform staff management of feature flags")
public class AdminFeatureFlagController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminFeatureFlagService service;
    private final UserIdResolverService userIdResolver;

    public AdminFeatureFlagController(AdminFeatureFlagService service, UserIdResolverService userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    private UUID actor(UserDetails principal) { return userIdResolver.resolve(principal); }

    @GetMapping
    public ResponseEntity<Page<AdminFlagResponse>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targeting,
            @RequestParam(required = false) String health,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.list(search, kind, status, targeting, health, sort,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @GetMapping("/kpis")
    public ResponseEntity<FeatureFlagKpisResponse> kpis() {
        return ResponseEntity.ok(service.kpis());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminFlagDetailResponse> getFlag(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getFlag(id));
    }

    @PostMapping
    public ResponseEntity<AdminFlagResponse> create(@Valid @RequestBody CreateFlagRequest req,
                                                    @AuthenticationPrincipal UserDetails principal,
                                                    HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.create(req, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminFlagResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateFlagRequest req,
                                                    @AuthenticationPrincipal UserDetails principal,
                                                    HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.update(id, req, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<AdminFlagResponse> enable(@PathVariable UUID id,
                                                    @AuthenticationPrincipal UserDetails principal,
                                                    HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.setEnabled(id, true, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<AdminFlagResponse> disable(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UserDetails principal,
                                                     HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.setEnabled(id, false, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/{id}/kill")
    public ResponseEntity<AdminFlagResponse> kill(@PathVariable UUID id,
                                                  @AuthenticationPrincipal UserDetails principal,
                                                  HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.kill(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<AdminFlagResponse> restore(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UserDetails principal,
                                                     HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.restore(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<AdminFlagResponse> archive(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UserDetails principal,
                                                     HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.archive(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<AdminFlagResponse> duplicate(@PathVariable UUID id,
                                                       @AuthenticationPrincipal UserDetails principal,
                                                       HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.duplicate(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }
}
