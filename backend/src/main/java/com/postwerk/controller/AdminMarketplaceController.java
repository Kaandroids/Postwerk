package com.postwerk.controller;

import com.postwerk.dto.admin.AdminListingDetailResponse;
import com.postwerk.dto.admin.AdminListingResponse;
import com.postwerk.dto.admin.AdminReviewResponse;
import com.postwerk.dto.admin.MarketplaceKpisResponse;
import com.postwerk.dto.admin.ModerationActionRequest;
import com.postwerk.service.AdminMarketplaceService;
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
 * Platform-staff Marketplace Moderation — cross-tenant listing + review moderation. Path-gated to
 * {@code ROLE_STAFF} by {@code SecurityConfig}; every endpoint additionally requires
 * {@code MARKETPLACE_MODERATE}.
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/admin/marketplace")
@PreAuthorize("hasAuthority('MARKETPLACE_MODERATE')")
@Tag(name = "Admin — Marketplace Moderation", description = "Platform staff moderation of marketplace listings & reviews")
public class AdminMarketplaceController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminMarketplaceService service;
    private final UserIdResolverService userIdResolver;

    public AdminMarketplaceController(AdminMarketplaceService service, UserIdResolverService userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    private UUID actor(UserDetails principal) { return userIdResolver.resolve(principal); }

    @GetMapping("/listings")
    public ResponseEntity<Page<AdminListingResponse>> listings(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String pricing,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.listListings(search, status, kind, pricing, sort,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @GetMapping("/reviews")
    public ResponseEntity<Page<AdminReviewResponse>> reviews(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.listReviews(search, rating, status, sort,
                PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE))));
    }

    @GetMapping("/kpis")
    public ResponseEntity<MarketplaceKpisResponse> kpis() {
        return ResponseEntity.ok(service.kpis());
    }

    @GetMapping("/listings/{id}")
    public ResponseEntity<AdminListingDetailResponse> getListing(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getListing(id));
    }

    @PostMapping("/listings/{id}/takedown")
    public ResponseEntity<AdminListingResponse> takeDown(@PathVariable UUID id,
                                                         @Valid @RequestBody ModerationActionRequest req,
                                                         @AuthenticationPrincipal UserDetails principal,
                                                         HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.takeDown(id, req.reason(), actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/listings/{id}/restore")
    public ResponseEntity<AdminListingResponse> restore(@PathVariable UUID id,
                                                        @AuthenticationPrincipal UserDetails principal,
                                                        HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.restore(id, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/listings/{id}/feature")
    public ResponseEntity<AdminListingResponse> feature(@PathVariable UUID id,
                                                        @AuthenticationPrincipal UserDetails principal,
                                                        HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.setFeatured(id, true, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/listings/{id}/unfeature")
    public ResponseEntity<AdminListingResponse> unfeature(@PathVariable UUID id,
                                                          @AuthenticationPrincipal UserDetails principal,
                                                          HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.setFeatured(id, false, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/reviews/{id}/hide")
    public ResponseEntity<AdminReviewResponse> hide(@PathVariable UUID id,
                                                    @AuthenticationPrincipal UserDetails principal,
                                                    HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.setReviewHidden(id, true, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @PostMapping("/reviews/{id}/unhide")
    public ResponseEntity<AdminReviewResponse> unhide(@PathVariable UUID id,
                                                      @AuthenticationPrincipal UserDetails principal,
                                                      HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.setReviewHidden(id, false, actor(principal), IpResolverUtil.extractIp(httpReq)));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID id,
                                             @RequestParam(required = false) String reason,
                                             @AuthenticationPrincipal UserDetails principal,
                                             HttpServletRequest httpReq) {
        // Permanently deleting a review requires a recorded justification (bounded to keep the audit detail sane).
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("A reason is required to delete a review.");
        if (clean.length() > 500) throw new IllegalArgumentException("Reason must be at most 500 characters.");
        service.deleteReview(id, clean, actor(principal), IpResolverUtil.extractIp(httpReq));
        return ResponseEntity.noContent().build();
    }
}
