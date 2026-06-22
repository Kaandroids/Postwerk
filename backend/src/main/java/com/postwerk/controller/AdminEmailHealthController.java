package com.postwerk.controller;

import com.postwerk.dto.admin.AdminMailboxHealthDetailResponse;
import com.postwerk.dto.admin.AdminMailboxHealthResponse;
import com.postwerk.dto.admin.EmailClusterSummaryResponse;
import com.postwerk.dto.admin.EmailHealthKpisResponse;
import com.postwerk.service.AdminEmailHealthService;
import com.postwerk.service.UserIdResolverService;
import com.postwerk.util.IpResolverUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
 * Platform-staff Email Health — cross-tenant IMAP/SMTP mailbox monitoring. Path-gated to
 * {@code ROLE_STAFF} by {@code SecurityConfig}; reads require {@code INFRA_VIEW}, mutations
 * (re-sync / pause / resume) require {@code INFRA_MANAGE}.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/email-health")
@Tag(name = "Admin — Email Health", description = "Platform staff monitoring of customer IMAP/SMTP mailboxes")
public class AdminEmailHealthController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminEmailHealthService service;
    private final UserIdResolverService userIdResolver;

    public AdminEmailHealthController(AdminEmailHealthService service, UserIdResolverService userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/mailboxes")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<Page<AdminMailboxHealthResponse>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String health,
            @RequestParam(required = false) String server,
            @RequestParam(required = false) String sync,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.listMailboxes(search, protocol, health, server, sync,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<EmailHealthKpisResponse> kpis() {
        return ResponseEntity.ok(service.kpis());
    }

    @GetMapping("/clusters")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<List<EmailClusterSummaryResponse>> clusters() {
        return ResponseEntity.ok(service.clusters());
    }

    @GetMapping("/mailboxes/{id}")
    @PreAuthorize("hasAuthority('INFRA_VIEW')")
    public ResponseEntity<AdminMailboxHealthDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getMailbox(id));
    }

    @PostMapping("/mailboxes/{id}/resync")
    @PreAuthorize("hasAuthority('INFRA_MANAGE')")
    public ResponseEntity<AdminMailboxHealthResponse> resync(@PathVariable UUID id,
                                                             @AuthenticationPrincipal UserDetails principal,
                                                             HttpServletRequest request) {
        return ResponseEntity.ok(service.resync(id, userIdResolver.resolve(principal), IpResolverUtil.extractIp(request)));
    }

    @PostMapping("/mailboxes/{id}/pause")
    @PreAuthorize("hasAuthority('INFRA_MANAGE')")
    public ResponseEntity<AdminMailboxHealthDetailResponse> pause(@PathVariable UUID id,
                                                                  @AuthenticationPrincipal UserDetails principal,
                                                                  HttpServletRequest request) {
        return ResponseEntity.ok(service.pause(id, userIdResolver.resolve(principal), IpResolverUtil.extractIp(request)));
    }

    @PostMapping("/mailboxes/{id}/resume")
    @PreAuthorize("hasAuthority('INFRA_MANAGE')")
    public ResponseEntity<AdminMailboxHealthDetailResponse> resume(@PathVariable UUID id,
                                                                   @AuthenticationPrincipal UserDetails principal,
                                                                   HttpServletRequest request) {
        return ResponseEntity.ok(service.resume(id, userIdResolver.resolve(principal), IpResolverUtil.extractIp(request)));
    }
}
