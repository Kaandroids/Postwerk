package com.postwerk.controller;

import com.postwerk.dto.admin.AdminAnnouncementDetailResponse;
import com.postwerk.dto.admin.AdminAnnouncementResponse;
import com.postwerk.dto.admin.AnnouncementKpisResponse;
import com.postwerk.dto.admin.AnnouncementRequest;
import com.postwerk.service.AdminAnnouncementService;
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
 * Platform-staff Announcements console. Path-gated to {@code ROLE_STAFF} by {@code SecurityConfig};
 * view + every mutation gate on {@code ANNOUNCEMENT_MANAGE} (the single capability for this surface).
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/announcements")
@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
@Tag(name = "Admin — Announcements", description = "Platform staff management of user-facing announcements")
public class AdminAnnouncementController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAnnouncementService service;
    private final UserIdResolverService userIdResolver;

    public AdminAnnouncementController(AdminAnnouncementService service, UserIdResolverService userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    private UUID actor(UserDetails principal) { return userIdResolver.resolve(principal); }

    @GetMapping
    public ResponseEntity<Page<AdminAnnouncementResponse>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String audience,
            @RequestParam(required = false) String placement,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.list(search, type, status, audience, placement, sort,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @GetMapping("/kpis")
    public ResponseEntity<AnnouncementKpisResponse> kpis() {
        return ResponseEntity.ok(service.kpis());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminAnnouncementDetailResponse> getAnnouncement(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getAnnouncement(id));
    }

    @PostMapping
    public ResponseEntity<AdminAnnouncementResponse> create(@Valid @RequestBody AnnouncementRequest req,
                                                            @AuthenticationPrincipal UserDetails principal,
                                                            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.create(req, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminAnnouncementResponse> update(@PathVariable UUID id,
                                                            @Valid @RequestBody AnnouncementRequest req,
                                                            @AuthenticationPrincipal UserDetails principal,
                                                            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.update(id, req, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<AdminAnnouncementResponse> publish(@PathVariable UUID id,
                                                             @AuthenticationPrincipal UserDetails principal,
                                                             HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.publish(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<AdminAnnouncementResponse> end(@PathVariable UUID id,
                                                         @AuthenticationPrincipal UserDetails principal,
                                                         HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.end(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<AdminAnnouncementResponse> archive(@PathVariable UUID id,
                                                             @AuthenticationPrincipal UserDetails principal,
                                                             HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.archive(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<AdminAnnouncementResponse> duplicate(@PathVariable UUID id,
                                                               @AuthenticationPrincipal UserDetails principal,
                                                               HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.duplicate(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }
}
