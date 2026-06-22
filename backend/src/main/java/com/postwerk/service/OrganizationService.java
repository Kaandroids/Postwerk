package com.postwerk.service;

import com.postwerk.dto.org.CreateOrganizationRequest;
import com.postwerk.dto.org.InvitationResponse;
import com.postwerk.dto.org.InviteMemberRequest;
import com.postwerk.dto.org.MailboxGrantInput;
import com.postwerk.dto.org.MailboxGrantResponse;
import com.postwerk.dto.org.MemberResponse;
import com.postwerk.dto.org.OrganizationDetailResponse;
import com.postwerk.dto.org.OrganizationResponse;
import com.postwerk.model.User;
import com.postwerk.model.enums.OrgRole;

import java.util.List;
import java.util.UUID;

/**
 * Organization & membership management (multi-tenant model #4, Phase B).
 *
 * @since 1.0
 */
public interface OrganizationService {

    /** Creates the auto-provisioned personal workspace + OWNER membership for a newly registered user. */
    void provisionPersonalOrg(User user);

    /** Organizations the caller belongs to (for the switcher). */
    List<OrganizationResponse> listMine(UUID userId);

    /** Creates a new collaborative organization owned by the caller. */
    OrganizationResponse create(UUID userId, CreateOrganizationRequest request);

    /** Full view of the active organization (members roster). */
    OrganizationDetailResponse getDetail(OrgContext ctx);

    /** Invites an existing registered user to the active organization as a pending member (requires MEMBER_INVITE). */
    MemberResponse invite(OrgContext ctx, InviteMemberRequest request);

    /** The caller's pending invitations (memberships in INVITED status), for the org switcher. */
    List<InvitationResponse> listInvitations(UUID userId);

    /** Accepts a pending invitation: the caller's membership in {@code organizationId} becomes ACTIVE. */
    OrganizationResponse acceptInvitation(UUID userId, UUID organizationId);

    /** Declines a pending invitation: the caller's INVITED membership in {@code organizationId} is removed. */
    void declineInvitation(UUID userId, UUID organizationId);

    /** Changes a member's role (requires MEMBER_MANAGE; OWNER cannot be assigned here). */
    MemberResponse setRole(OrgContext ctx, UUID targetUserId, OrgRole role);

    /** Removes a member from the active organization (requires MEMBER_MANAGE). */
    void removeMember(OrgContext ctx, UUID targetUserId);

    /** The caller leaves the active organization (owners must transfer/delete instead). */
    void leave(OrgContext ctx);

    /** A member's per-mailbox grants — one entry per org mailbox (requires MEMBER_MANAGE). */
    List<MailboxGrantResponse> getMailboxGrants(OrgContext ctx, UUID targetUserId);

    /** Replaces a member's per-mailbox grants (requires MEMBER_MANAGE). */
    void setMailboxGrants(OrgContext ctx, UUID targetUserId, List<MailboxGrantInput> grants);
}
