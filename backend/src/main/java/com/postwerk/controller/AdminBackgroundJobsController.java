package com.postwerk.controller;

import com.postwerk.dto.admin.BackgroundJobsKpisResponse;
import com.postwerk.dto.admin.JobDetailResponse;
import com.postwerk.dto.admin.JobQueueResponse;
import com.postwerk.dto.admin.JobResponse;
import com.postwerk.service.AdminBackgroundJobsService;
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
 * Platform-staff Background Jobs — the platform's recurring scheduled jobs + work queues. Path-gated
 * to {@code ROLE_STAFF} by {@code SecurityConfig}; reads need {@code INFRA_VIEW}, mutations
 * (run-now / pause / resume) need {@code INFRA_MANAGE}.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/background-jobs")
@Tag(name = "Admin — Background Jobs", description = "Platform staff oversight of scheduled jobs & work queues")
public class AdminBackgroundJobsController {

    private final AdminBackgroundJobsService service;
    private final UserIdResolverService userIdResolver;

    public AdminBackgroundJobsController(AdminBackgroundJobsService service, UserIdResolverService userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<List<JobResponse>> jobs() {
        return ResponseEntity.ok(service.listJobs());
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<BackgroundJobsKpisResponse> kpis() {
        return ResponseEntity.ok(service.kpis());
    }

    @GetMapping("/queues")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<List<JobQueueResponse>> queues() {
        return ResponseEntity.ok(service.queues());
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<JobDetailResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(service.getJob(id));
    }

    @PostMapping("/jobs/{id}/run")
    @PreAuthorize("hasAuthority('INFRA_MANAGE')")
    public ResponseEntity<JobResponse> run(@PathVariable String id,
                                           @AuthenticationPrincipal UserDetails principal,
                                           HttpServletRequest request) {
        return ResponseEntity.ok(service.runNow(id, userIdResolver.resolve(principal), IpResolverUtil.extractIp(request)));
    }

    @PostMapping("/jobs/{id}/pause")
    @PreAuthorize("hasAuthority('INFRA_MANAGE')")
    public ResponseEntity<JobResponse> pause(@PathVariable String id,
                                             @AuthenticationPrincipal UserDetails principal,
                                             HttpServletRequest request) {
        return ResponseEntity.ok(service.setPaused(id, true, userIdResolver.resolve(principal), IpResolverUtil.extractIp(request)));
    }

    @PostMapping("/jobs/{id}/resume")
    @PreAuthorize("hasAuthority('INFRA_MANAGE')")
    public ResponseEntity<JobResponse> resume(@PathVariable String id,
                                              @AuthenticationPrincipal UserDetails principal,
                                              HttpServletRequest request) {
        return ResponseEntity.ok(service.setPaused(id, false, userIdResolver.resolve(principal), IpResolverUtil.extractIp(request)));
    }
}
