package com.postwerk.controller;

import com.postwerk.dto.*;
import com.postwerk.model.enums.Permission;
import com.postwerk.service.MarketplaceService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.UserIdResolverService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the automation marketplace.
 *
 * <p>Discovery / listing detail / reviews are public-catalog reads (per-user flags). Publishing
 * requires MARKETPLACE_PUBLISH; installing and configuring/activating an installed copy require
 * MARKETPLACE_INSTALL. The library and installed-copy configuration are scoped to the active
 * organization (#4).</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/marketplace")
@Tag(name = "Marketplace", description = "Automation marketplace: discover, publish, install, configure")
public class MarketplaceController {

    private final MarketplaceService marketplaceService;
    private final OrgContextService orgContext;
    private final UserIdResolverService userIdResolver;

    public MarketplaceController(MarketplaceService marketplaceService,
                                 OrgContextService orgContext,
                                 UserIdResolverService userIdResolver) {
        this.marketplaceService = marketplaceService;
        this.orgContext = orgContext;
        this.userIdResolver = userIdResolver;
    }

    /** Discover published listings, optionally filtered by category/search and sorted. */
    @GetMapping("/listings")
    public ResponseEntity<List<MarketplaceListingResponse>> discover(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String cat,
            @RequestParam(required = false, defaultValue = "popular") String sort,
            @RequestParam(required = false) String q) {
        UUID userId = userIdResolver.resolve(userDetails);
        return ResponseEntity.ok(marketplaceService.discover(userId, cat, sort, q));
    }

    /** Full detail of a listing (incl. reviews + public node-flow or private publishable constants). */
    @GetMapping("/listings/{id}")
    public ResponseEntity<MarketplaceListingDetailResponse> detail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = userIdResolver.resolve(userDetails);
        return ResponseEntity.ok(marketplaceService.getDetail(userId, id));
    }

    /** Publish one of the organization's automations as a listing. */
    @PostMapping("/listings")
    public ResponseEntity<MarketplaceListingDetailResponse> publish(
            OrgContext ctx,
            @Valid @RequestBody PublishListingRequest request) {
        orgContext.require(ctx, Permission.MARKETPLACE_PUBLISH);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(marketplaceService.publish(ctx.organizationId(), ctx.userId(), request));
    }

    /** Install a listing — creates a buyer-owned (hidden, for PRIVATE) copy + entitlement. */
    @PostMapping("/listings/{id}/install")
    public ResponseEntity<MarketplaceAcquisitionResponse> install(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.MARKETPLACE_INSTALL);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(marketplaceService.install(ctx.organizationId(), ctx.userId(), id));
    }

    /** Unpublish a listing owned by the author. */
    @DeleteMapping("/listings/{id}")
    public ResponseEntity<Void> unpublish(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.MARKETPLACE_PUBLISH);
        marketplaceService.unpublish(ctx.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** The active organization's library: installed / purchased automations. */
    @GetMapping("/library")
    public ResponseEntity<MarketplaceLibraryResponse> library(OrgContext ctx) {
        return ResponseEntity.ok(marketplaceService.getLibrary(ctx.organizationId()));
    }

    /** Save buyer-overridable constant values for an installed (hidden) automation. */
    @PutMapping("/acquisitions/{id}/constants")
    public ResponseEntity<Void> saveConstants(
            OrgContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody ConstantsUpdateRequest request) {
        orgContext.require(ctx, Permission.MARKETPLACE_INSTALL);
        marketplaceService.saveAcquisitionConstants(ctx.organizationId(), id, request.constants());
        return ResponseEntity.noContent().build();
    }

    /** Bind the org's email accounts to the installed automation's trigger. */
    @PutMapping("/acquisitions/{id}/accounts")
    public ResponseEntity<Void> bindAccounts(
            OrgContext ctx,
            @PathVariable UUID id,
            @Valid @RequestBody AccountBindingRequest request) {
        orgContext.require(ctx, Permission.MARKETPLACE_INSTALL);
        marketplaceService.bindAccounts(ctx.organizationId(), id, request.accountIds());
        return ResponseEntity.noContent().build();
    }

    /** Activate the installed automation. */
    @PostMapping("/acquisitions/{id}/activate")
    public ResponseEntity<MarketplaceAcquisitionResponse> activate(
            OrgContext ctx,
            @PathVariable UUID id) {
        orgContext.require(ctx, Permission.MARKETPLACE_INSTALL);
        return ResponseEntity.ok(marketplaceService.activate(ctx.organizationId(), id));
    }

    /** List reviews for a listing. */
    @GetMapping("/listings/{id}/reviews")
    public ResponseEntity<List<ReviewResponse>> reviews(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = userIdResolver.resolve(userDetails);
        return ResponseEntity.ok(marketplaceService.getReviews(userId, id));
    }

    /** Create or update the requesting user's review of a listing. */
    @PostMapping("/listings/{id}/reviews")
    public ResponseEntity<ReviewResponse> addReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request) {
        UUID userId = userIdResolver.resolve(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED).body(marketplaceService.addReview(userId, id, request));
    }
}
