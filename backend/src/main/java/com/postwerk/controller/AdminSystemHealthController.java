package com.postwerk.controller;

import com.postwerk.dto.admin.MaintenanceModeRequest;
import com.postwerk.dto.admin.MaintenanceModeResponse;
import com.postwerk.dto.admin.SubsystemHealthResponse;
import com.postwerk.dto.admin.SystemHealthEventResponse;
import com.postwerk.dto.admin.SystemHealthKpisResponse;
import com.postwerk.service.AdminSystemHealthService;
import com.postwerk.service.UserIdResolverService;
import com.postwerk.util.IpResolverUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Platform-staff System Health — live monitoring of Postwerk's own subsystems. Path-gated to
 * {@code ROLE_STAFF} by {@code SecurityConfig}; reads require {@code INFRA_VIEW}, mutations
 * (re-probe / cache flush / maintenance toggle) require {@code INFRA_MANAGE}.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/system-health")
@Tag(name = "Admin — System Health", description = "Platform staff monitoring of Postwerk's own subsystems")
public class AdminSystemHealthController {

    private final AdminSystemHealthService service;
    private final UserIdResolverService userIdResolver;

    public AdminSystemHealthController(AdminSystemHealthService service, UserIdResolverService userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/subsystems")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<List<SubsystemHealthResponse>> subsystems() {
        return ResponseEntity.ok(service.subsystems());
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<SystemHealthKpisResponse> kpis() {
        return ResponseEntity.ok(service.kpis());
    }

    @GetMapping("/events")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<List<SystemHealthEventResponse>> events() {
        return ResponseEntity.ok(service.events());
    }

    @GetMapping("/subsystems/{id}")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<SubsystemHealthResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(service.getSubsystem(id));
    }

    @PostMapping("/subsystems/{id}/probe")
    @PreAuthorize("hasAuthority('INFRA_MANAGE')")
    public ResponseEntity<SubsystemHealthResponse> probe(@PathVariable String id,
                                                         @AuthenticationPrincipal UserDetails principal,
                                                         HttpServletRequest request) {
        return ResponseEntity.ok(service.probe(id, userIdResolver.resolve(principal), IpResolverUtil.extractIp(request)));
    }

    @PostMapping("/cache/flush")
    @PreAuthorize("hasAuthority('INFRA_MANAGE')")
    public ResponseEntity<Void> flushCache(@AuthenticationPrincipal UserDetails principal,
                                           HttpServletRequest request) {
        service.flushCache(userIdResolver.resolve(principal), IpResolverUtil.extractIp(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/maintenance")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<MaintenanceModeResponse> getMaintenance() {
        return ResponseEntity.ok(service.getMaintenance());
    }

    @PutMapping("/maintenance")
    @PreAuthorize("hasAuthority('INFRA_MANAGE')")
    public ResponseEntity<MaintenanceModeResponse> setMaintenance(@RequestBody MaintenanceModeRequest request,
                                                                  @AuthenticationPrincipal UserDetails principal,
                                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.setMaintenance(
                request.enabled(), request.message(),
                userIdResolver.resolve(principal), IpResolverUtil.extractIp(httpRequest)));
    }
}
