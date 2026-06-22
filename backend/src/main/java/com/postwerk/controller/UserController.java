package com.postwerk.controller;

import com.postwerk.dto.*;
import com.postwerk.dto.auth.MessageResponse;
import com.postwerk.model.Plan;
import com.postwerk.repository.PlanRepository;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.QuotaService;
import com.postwerk.service.UserIdResolverService;
import com.postwerk.service.UserService;
import com.postwerk.util.IpResolverUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user profile management.
 *
 * <p>Handles profile retrieval, updates, password changes, GDPR consent management,
 * account deletion, and data exports. All endpoints require authentication.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Profile", description = "Profile management, password changes, and GDPR consent")
public class UserController {

    private final UserService userService;
    private final UserIdResolverService userIdResolver;
    private final PlanRepository planRepository;
    private final QuotaService quotaService;
    private final OrgContextService orgContext;

    public UserController(UserService userService, UserIdResolverService userIdResolver,
                          PlanRepository planRepository, QuotaService quotaService,
                          OrgContextService orgContext) {
        this.userService = userService;
        this.userIdResolver = userIdResolver;
        this.planRepository = planRepository;
        this.quotaService = quotaService;
        this.orgContext = orgContext;
    }

    /** Returns all available subscription plans. */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanSummaryResponse>> getPlans() {
        List<PlanSummaryResponse> plans = planRepository.findAll().stream()
                .map(p -> new PlanSummaryResponse(p.getId(), p.getName(), p.getTokenLimit(),
                        p.getAutomationLimit(), p.getEmailAccountLimit(), p.getPrice(),
                        p.isApiWebhookEnabled(), p.getCostLimitCents()))
                .toList();
        return ResponseEntity.ok(plans);
    }

    /** Returns the active organization's plan usage and limits (#4). */
    @GetMapping("/me/usage")
    public ResponseEntity<UsageResponse> getUsage(@AuthenticationPrincipal UserDetails userDetails,
                                                  HttpServletRequest httpRequest) {
        OrgContext ctx = orgContext.resolve(userDetails, httpRequest);
        return ResponseEntity.ok(quotaService.getUsage(ctx.organizationId()));
    }

    /** Returns the authenticated user's profile. */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userIdResolver.resolve(userDetails);
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    /** Updates the authenticated user's profile fields. */
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                                              @Valid @RequestBody UpdateProfileRequest request,
                                                              HttpServletRequest httpRequest) {
        UUID userId = userIdResolver.resolve(userDetails);
        return ResponseEntity.ok(userService.updateProfile(userId, request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Changes the authenticated user's password after verifying the current one. */
    @PostMapping("/me/change-password")
    public ResponseEntity<MessageResponse> changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                                           @Valid @RequestBody ChangePasswordRequest request,
                                                           HttpServletRequest httpRequest) {
        UUID userId = userIdResolver.resolve(userDetails);
        userService.changePassword(userId, request, IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.ok(new MessageResponse("Password changed successfully"));
    }

    /** Updates GDPR marketing consent preference. */
    @PatchMapping("/me/consent")
    public ResponseEntity<MessageResponse> updateConsent(@AuthenticationPrincipal UserDetails userDetails,
                                                          @Valid @RequestBody ConsentUpdateRequest request,
                                                          HttpServletRequest httpRequest) {
        UUID userId = userIdResolver.resolve(userDetails);
        if (request.marketingOptIn() != null) {
            userService.updateConsent(userId, request.marketingOptIn(), IpResolverUtil.extractIp(httpRequest));
        }
        return ResponseEntity.ok(new MessageResponse("Consent updated successfully"));
    }

    /** Soft-deletes the authenticated user's account and all associated data. */
    @DeleteMapping("/me")
    public ResponseEntity<MessageResponse> deleteAccount(@AuthenticationPrincipal UserDetails userDetails,
                                                          HttpServletRequest httpRequest) {
        UUID userId = userIdResolver.resolve(userDetails);
        userService.deleteAccount(userId, IpResolverUtil.extractIp(httpRequest));
        return ResponseEntity.ok(new MessageResponse("Account deleted successfully"));
    }

    /** Exports all user data as a JSON download (GDPR Art. 20 — data portability). */
    @GetMapping("/me/export")
    public ResponseEntity<UserExportResponse> exportData(@AuthenticationPrincipal UserDetails userDetails,
                                                          HttpServletRequest httpRequest) {
        UUID userId = userIdResolver.resolve(userDetails);
        UserExportResponse export = userService.exportData(userId, IpResolverUtil.extractIp(httpRequest));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("postwerk-data-export.json")
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(export);
    }
}
