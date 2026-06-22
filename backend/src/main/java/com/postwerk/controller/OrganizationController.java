package com.postwerk.controller;

import com.postwerk.dto.org.CreateOrganizationRequest;
import com.postwerk.dto.org.InvitationResponse;
import com.postwerk.dto.org.InviteMemberRequest;
import com.postwerk.dto.org.MailboxGrantInput;
import com.postwerk.dto.org.MailboxGrantResponse;
import com.postwerk.dto.org.MemberResponse;
import com.postwerk.dto.org.OrganizationDetailResponse;
import com.postwerk.dto.org.OrganizationResponse;
import com.postwerk.dto.org.UpdateMemberRoleRequest;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.OrganizationService;
import com.postwerk.service.UserIdResolverService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for organization & membership management (multi-tenant model #4, Phase B).
 *
 * <p>Collection endpoints (list/create) operate on the authenticated user. The {@code current}
 * endpoint and member sub-resources operate on the <em>active</em> organization, resolved from the
 * {@code X-Org-Id} header. Member mutations are permission-gated inside the service.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Organization & membership management")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrgContextService orgContextService;
    private final UserIdResolverService userIdResolver;

    public OrganizationController(OrganizationService organizationService,
                                  OrgContextService orgContextService,
                                  UserIdResolverService userIdResolver) {
        this.organizationService = organizationService;
        this.orgContextService = orgContextService;
        this.userIdResolver = userIdResolver;
    }

    /** Organizations the caller belongs to (for the switcher). */
    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> listMine(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(organizationService.listMine(userIdResolver.resolve(userDetails)));
    }

    /** Creates a new collaborative organization owned by the caller. */
    @PostMapping
    public ResponseEntity<OrganizationResponse> create(@AuthenticationPrincipal UserDetails userDetails,
                                                       @Valid @RequestBody CreateOrganizationRequest request) {
        UUID userId = userIdResolver.resolve(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.create(userId, request));
    }

    /** Full view (members roster) of the active organization. */
    @GetMapping("/current")
    public ResponseEntity<OrganizationDetailResponse> current(@AuthenticationPrincipal UserDetails userDetails,
                                                              HttpServletRequest httpRequest) {
        OrgContext ctx = orgContextService.resolve(userDetails, httpRequest);
        return ResponseEntity.ok(organizationService.getDetail(ctx));
    }

    /** Adds an existing registered user to the active organization. */
    @PostMapping("/members")
    public ResponseEntity<MemberResponse> invite(@AuthenticationPrincipal UserDetails userDetails,
                                                 @Valid @RequestBody InviteMemberRequest request,
                                                 HttpServletRequest httpRequest) {
        OrgContext ctx = orgContextService.resolve(userDetails, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.invite(ctx, request));
    }

    /** The caller's pending invitations (operates on the authenticated user, not the active org). */
    @GetMapping("/invitations")
    public ResponseEntity<List<InvitationResponse>> listInvitations(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(organizationService.listInvitations(userIdResolver.resolve(userDetails)));
    }

    /** Accepts a pending invitation; the membership becomes ACTIVE. */
    @PostMapping("/invitations/{orgId}/accept")
    public ResponseEntity<OrganizationResponse> acceptInvitation(@AuthenticationPrincipal UserDetails userDetails,
                                                                 @PathVariable UUID orgId) {
        UUID userId = userIdResolver.resolve(userDetails);
        return ResponseEntity.ok(organizationService.acceptInvitation(userId, orgId));
    }

    /** Declines a pending invitation; the INVITED membership is removed. */
    @PostMapping("/invitations/{orgId}/decline")
    public ResponseEntity<Void> declineInvitation(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable UUID orgId) {
        UUID userId = userIdResolver.resolve(userDetails);
        organizationService.declineInvitation(userId, orgId);
        return ResponseEntity.noContent().build();
    }

    /** Changes a member's role in the active organization. */
    @PutMapping("/members/{userId}")
    public ResponseEntity<MemberResponse> setRole(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable UUID userId,
                                                  @Valid @RequestBody UpdateMemberRoleRequest request,
                                                  HttpServletRequest httpRequest) {
        OrgContext ctx = orgContextService.resolve(userDetails, httpRequest);
        return ResponseEntity.ok(organizationService.setRole(ctx, userId, request.role()));
    }

    /** Removes a member from the active organization. */
    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal UserDetails userDetails,
                                             @PathVariable UUID userId,
                                             HttpServletRequest httpRequest) {
        OrgContext ctx = orgContextService.resolve(userDetails, httpRequest);
        organizationService.removeMember(ctx, userId);
        return ResponseEntity.noContent().build();
    }

    /** The caller leaves the active organization. */
    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal UserDetails userDetails,
                                      HttpServletRequest httpRequest) {
        OrgContext ctx = orgContextService.resolve(userDetails, httpRequest);
        organizationService.leave(ctx);
        return ResponseEntity.noContent().build();
    }

    /** Lists a member's per-mailbox grants (one entry per org mailbox). */
    @GetMapping("/members/{userId}/mailbox-grants")
    public ResponseEntity<List<MailboxGrantResponse>> getMailboxGrants(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            HttpServletRequest httpRequest) {
        OrgContext ctx = orgContextService.resolve(userDetails, httpRequest);
        return ResponseEntity.ok(organizationService.getMailboxGrants(ctx, userId));
    }

    /** Replaces a member's per-mailbox grants. */
    @PutMapping("/members/{userId}/mailbox-grants")
    public ResponseEntity<Void> setMailboxGrants(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @RequestBody List<MailboxGrantInput> grants,
            HttpServletRequest httpRequest) {
        OrgContext ctx = orgContextService.resolve(userDetails, httpRequest);
        organizationService.setMailboxGrants(ctx, userId, grants);
        return ResponseEntity.noContent().build();
    }
}
