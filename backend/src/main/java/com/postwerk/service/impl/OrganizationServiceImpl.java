package com.postwerk.service.impl;

import com.postwerk.dto.org.CreateOrganizationRequest;
import com.postwerk.dto.org.InvitationResponse;
import com.postwerk.dto.org.InviteMemberRequest;
import com.postwerk.dto.org.MailboxGrantInput;
import com.postwerk.dto.org.MailboxGrantResponse;
import com.postwerk.dto.org.MemberResponse;
import com.postwerk.dto.org.OrganizationDetailResponse;
import com.postwerk.dto.org.OrganizationResponse;
import com.postwerk.event.TeamInvitedEvent;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.EmailAccount;
import com.postwerk.model.MailboxGrant;
import com.postwerk.model.Membership;
import com.postwerk.model.Organization;
import com.postwerk.model.Plan;
import com.postwerk.model.User;
import com.postwerk.model.enums.MembershipStatus;
import com.postwerk.model.enums.OrgRole;
import com.postwerk.model.enums.Permission;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.MailboxGrantRepository;
import com.postwerk.repository.MembershipRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.PlanRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.OrgContext;
import com.postwerk.service.OrgContextService;
import com.postwerk.service.OrganizationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link OrganizationService} implementation (multi-tenant model #4, Phase B).
 *
 * @since 1.0
 */
@Service
public class OrganizationServiceImpl implements OrganizationService {

    /** Max free (Starter) organizations a single account may own, personal workspace included. Paid orgs are unlimited. */
    private static final int MAX_FREE_ORGS_PER_OWNER = 2;

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final OrgContextService orgContextService;
    private final MailboxGrantRepository mailboxGrantRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrganizationServiceImpl(OrganizationRepository organizationRepository,
                                   MembershipRepository membershipRepository,
                                   UserRepository userRepository,
                                   PlanRepository planRepository,
                                   OrgContextService orgContextService,
                                   MailboxGrantRepository mailboxGrantRepository,
                                   EmailAccountRepository emailAccountRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.orgContextService = orgContextService;
        this.mailboxGrantRepository = mailboxGrantRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void provisionPersonalOrg(User user) {
        if (organizationRepository.findByOwnerUserIdAndPersonalTrue(user.getId()).isPresent()) {
            return; // idempotent — already provisioned
        }
        Organization org = organizationRepository.save(Organization.builder()
                .name(orgNameFor(user))
                .ownerUserId(user.getId())
                .personal(true)
                .plan(user.getPlan())
                .build());
        membershipRepository.save(Membership.builder()
                .organizationId(org.getId())
                .userId(user.getId())
                .role(OrgRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponse> listMine(UUID userId) {
        List<OrganizationResponse> result = new ArrayList<>();
        for (Membership m : membershipRepository.findByUserId(userId)) {
            if (m.getStatus() != MembershipStatus.ACTIVE) continue;
            Organization org = organizationRepository.findById(m.getOrganizationId()).orElse(null);
            if (org == null) continue; // soft-deleted org
            long memberCount = membershipRepository.countByOrganizationId(org.getId());
            result.add(toResponse(org, m.getRole(), memberCount));
        }
        return result;
    }

    @Override
    @Transactional
    public OrganizationResponse create(UUID userId, CreateOrganizationRequest request) {
        Plan defaultPlan = planRepository.findByName(Plan.DEFAULT_PLAN_NAME).orElse(null);
        // New orgs are created on the free (Starter) plan, so they count toward the free-org cap.
        // Paid orgs (price > 0) are excluded and unlimited.
        boolean newOrgIsFree = defaultPlan == null || defaultPlan.getPrice() == null
                || defaultPlan.getPrice().signum() <= 0;
        if (newOrgIsFree && organizationRepository.countOwnedFreeOrgs(userId) >= MAX_FREE_ORGS_PER_OWNER) {
            throw new IllegalArgumentException(
                    "Free-plan organization limit reached (max " + MAX_FREE_ORGS_PER_OWNER
                    + "). Upgrade an organization to a paid plan to create more.");
        }
        Organization org = organizationRepository.save(Organization.builder()
                .name(request.name().trim())
                .ownerUserId(userId)
                .personal(false)
                .plan(defaultPlan)
                .build());
        membershipRepository.save(Membership.builder()
                .organizationId(org.getId())
                .userId(userId)
                .role(OrgRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build());
        return toResponse(org, OrgRole.OWNER, 1);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationDetailResponse getDetail(OrgContext ctx) {
        Organization org = loadOrg(ctx.organizationId());
        List<Membership> memberships = membershipRepository.findByOrganizationId(org.getId());

        List<UUID> userIds = memberships.stream().map(Membership::getUserId).toList();
        Map<UUID, User> usersById = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> usersById.put(u.getId(), u));

        List<MemberResponse> members = memberships.stream()
                .map(m -> {
                    User u = usersById.get(m.getUserId());
                    if (u == null) return null; // soft-deleted user
                    return new MemberResponse(u.getId(), u.getEmail(), u.getFullName(), m.getRole(), m.getStatus());
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        return new OrganizationDetailResponse(org.getId(), org.getName(), org.getSlug(), org.isPersonal(),
                ctx.role(), planName(org), members);
    }

    @Override
    @Transactional
    public MemberResponse invite(OrgContext ctx, InviteMemberRequest request) {
        orgContextService.require(ctx, Permission.MEMBER_INVITE);
        Organization org = loadOrg(ctx.organizationId());
        if (org.isPersonal()) {
            throw new IllegalArgumentException("A personal workspace cannot have additional members");
        }
        OrgRole role = request.role() != null ? request.role() : OrgRole.MEMBER;
        if (role == OrgRole.OWNER) {
            throw new IllegalArgumentException("Cannot invite a member as OWNER");
        }
        User user = userRepository.findByEmail(request.email().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.email()));
        if (membershipRepository.findByOrganizationIdAndUserId(org.getId(), user.getId()).isPresent()) {
            throw new IllegalArgumentException("User is already a member of this organization");
        }
        // Invited, not active: the invitee gains no access until they accept (OrgContextService
        // only resolves ACTIVE memberships, and listMine hides non-active ones).
        Membership m = membershipRepository.save(Membership.builder()
                .organizationId(org.getId())
                .userId(user.getId())
                .role(role)
                .status(MembershipStatus.INVITED)
                .invitedByUserId(ctx.userId())
                .build());

        String inviterName = userRepository.findById(ctx.userId())
                .map(u -> u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail())
                .orElse(null);
        eventPublisher.publishEvent(new TeamInvitedEvent(org.getId(), user.getId(), org.getName(), inviterName));

        return new MemberResponse(user.getId(), user.getEmail(), user.getFullName(), m.getRole(), m.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationResponse> listInvitations(UUID userId) {
        List<InvitationResponse> result = new ArrayList<>();
        for (Membership m : membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.INVITED)) {
            Organization org = organizationRepository.findById(m.getOrganizationId()).orElse(null);
            if (org == null) continue; // soft-deleted org
            String inviterName = m.getInvitedByUserId() == null ? null
                    : userRepository.findById(m.getInvitedByUserId())
                        .map(u -> u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail())
                        .orElse(null);
            result.add(new InvitationResponse(org.getId(), org.getName(), m.getRole(), inviterName));
        }
        return result;
    }

    @Override
    @Transactional
    public OrganizationResponse acceptInvitation(UUID userId, UUID organizationId) {
        Membership m = membershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", organizationId));
        if (m.getStatus() != MembershipStatus.INVITED) {
            throw new IllegalArgumentException("No pending invitation for this organization");
        }
        m.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(m);
        Organization org = loadOrg(organizationId);
        long memberCount = membershipRepository.countByOrganizationId(org.getId());
        return toResponse(org, m.getRole(), memberCount);
    }

    @Override
    @Transactional
    public void declineInvitation(UUID userId, UUID organizationId) {
        Membership m = membershipRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", organizationId));
        if (m.getStatus() != MembershipStatus.INVITED) {
            throw new IllegalArgumentException("No pending invitation for this organization");
        }
        membershipRepository.delete(m); // mailbox_grants cascade via FK (none exist yet for an invite)
    }

    @Override
    @Transactional
    public MemberResponse setRole(OrgContext ctx, UUID targetUserId, OrgRole role) {
        orgContextService.require(ctx, Permission.MEMBER_MANAGE);
        if (role == OrgRole.OWNER) {
            throw new IllegalArgumentException("Ownership transfer is not supported here");
        }
        Membership m = membershipRepository.findByOrganizationIdAndUserId(ctx.organizationId(), targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", targetUserId));
        if (m.getRole() == OrgRole.OWNER) {
            throw new IllegalArgumentException("Cannot change the owner's role");
        }
        m.setRole(role);
        membershipRepository.save(m);
        User u = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));
        return new MemberResponse(u.getId(), u.getEmail(), u.getFullName(), m.getRole(), m.getStatus());
    }

    @Override
    @Transactional
    public void removeMember(OrgContext ctx, UUID targetUserId) {
        orgContextService.require(ctx, Permission.MEMBER_MANAGE);
        if (targetUserId.equals(ctx.userId())) {
            throw new IllegalArgumentException("Use 'leave' to remove yourself from an organization");
        }
        Membership m = membershipRepository.findByOrganizationIdAndUserId(ctx.organizationId(), targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", targetUserId));
        if (m.getRole() == OrgRole.OWNER) {
            throw new IllegalArgumentException("Cannot remove the organization owner");
        }
        membershipRepository.delete(m); // mailbox_grants cascade via FK
    }

    @Override
    @Transactional
    public void leave(OrgContext ctx) {
        Membership m = membershipRepository.findByOrganizationIdAndUserId(ctx.organizationId(), ctx.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Membership", ctx.userId()));
        if (m.getRole() == OrgRole.OWNER) {
            throw new IllegalArgumentException("The owner cannot leave; transfer ownership or delete the organization");
        }
        membershipRepository.delete(m);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MailboxGrantResponse> getMailboxGrants(OrgContext ctx, UUID targetUserId) {
        orgContextService.require(ctx, Permission.MEMBER_MANAGE);
        Membership target = requireMember(ctx.organizationId(), targetUserId);

        Map<UUID, MailboxGrant> grantsByMailbox = new HashMap<>();
        mailboxGrantRepository.findByMembershipId(target.getId())
                .forEach(g -> grantsByMailbox.put(g.getMailboxId(), g));

        // One entry per org mailbox so the editor can show every inbox with its current access.
        return emailAccountRepository.findByOrganizationId(ctx.organizationId()).stream()
                .map(mb -> {
                    MailboxGrant g = grantsByMailbox.get(mb.getId());
                    return new MailboxGrantResponse(mb.getId(), mb.getEmail(),
                            g != null && g.isCanRead(), g != null && g.isCanSend());
                })
                .toList();
    }

    @Override
    @Transactional
    public void setMailboxGrants(OrgContext ctx, UUID targetUserId, List<MailboxGrantInput> grants) {
        orgContextService.require(ctx, Permission.MEMBER_MANAGE);
        Membership target = requireMember(ctx.organizationId(), targetUserId);

        Set<UUID> orgMailboxIds = emailAccountRepository.findByOrganizationId(ctx.organizationId()).stream()
                .map(EmailAccount::getId)
                .collect(Collectors.toSet());

        // Replace the member's grants wholesale (only persist rows that grant something).
        mailboxGrantRepository.deleteByMembershipId(target.getId());
        if (grants == null) return;
        for (MailboxGrantInput in : grants) {
            if (in == null || in.mailboxId() == null) continue;
            if (!orgMailboxIds.contains(in.mailboxId())) {
                throw new IllegalArgumentException("Mailbox does not belong to this organization");
            }
            if (!in.canRead() && !in.canSend()) continue;
            mailboxGrantRepository.save(MailboxGrant.builder()
                    .membershipId(target.getId())
                    .mailboxId(in.mailboxId())
                    .canRead(in.canRead())
                    .canSend(in.canSend())
                    .build());
        }
    }

    // ─── helpers ───

    private Membership requireMember(UUID organizationId, UUID targetUserId) {
        return membershipRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", targetUserId));
    }

    private Organization loadOrg(UUID orgId) {
        return organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));
    }

    private OrganizationResponse toResponse(Organization org, OrgRole myRole, long memberCount) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getSlug(), org.isPersonal(),
                myRole, memberCount, planName(org));
    }

    private String planName(Organization org) {
        return org.getPlan() != null ? org.getPlan().getName() : null;
    }

    private String orgNameFor(User user) {
        String full = user.getFullName() != null ? user.getFullName().trim() : "";
        return !full.isEmpty() ? full : user.getEmail();
    }
}
