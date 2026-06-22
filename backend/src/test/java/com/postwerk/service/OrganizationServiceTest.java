package com.postwerk.service;

import com.postwerk.TestFixtures;
import com.postwerk.dto.org.CreateOrganizationRequest;
import com.postwerk.dto.org.InviteMemberRequest;
import com.postwerk.event.TeamInvitedEvent;
import com.postwerk.exception.ResourceNotFoundException;
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
import com.postwerk.service.impl.OrganizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrganizationServiceImpl} covering the team flows added with the role redesign
 * (#4): the pending invitation lifecycle (invite → INVITED + {@link TeamInvitedEvent}; accept; decline)
 * and the free-org ownership cap. Authorization itself is delegated to the (mocked)
 * {@link OrgContextService}, so {@code require(...)} is a no-op here — these tests assert the service's
 * own business rules and that the correct permission is demanded.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;
    @Mock private OrgContextService orgContextService;
    @Mock private MailboxGrantRepository mailboxGrantRepository;
    @Mock private EmailAccountRepository emailAccountRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrganizationServiceImpl service;

    private UUID orgId;
    private UUID inviterId;
    private UUID inviteeId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        inviterId = UUID.randomUUID();
        inviteeId = UUID.randomUUID();
    }

    private OrgContext ownerCtx() {
        return new OrgContext(orgId, inviterId, UUID.randomUUID(),
                OrgRole.OWNER, OrgRole.OWNER.permissions(), true);
    }

    private Organization team(boolean personal) {
        return Organization.builder().id(orgId).name("Acme").ownerUserId(inviterId).personal(personal).build();
    }

    // ── invite ───────────────────────────────────────────────────────────

    @Test
    void invite_createsInvitedMembership_andPublishesEvent() {
        User invitee = TestFixtures.createUser("new@example.com");
        invitee.setId(inviteeId);
        User inviter = TestFixtures.createUser("owner@example.com");
        inviter.setId(inviterId);
        inviter.setFullName("Org Owner");

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(team(false)));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.of(invitee));
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, inviteeId)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(inviterId)).thenReturn(Optional.of(inviter));

        var ctx = ownerCtx();
        var response = service.invite(ctx, new InviteMemberRequest("new@example.com", OrgRole.EDITOR));

        assertThat(response.status()).isEqualTo(MembershipStatus.INVITED);
        assertThat(response.role()).isEqualTo(OrgRole.EDITOR);

        // Authorization demanded the right permission.
        verify(orgContextService).require(ctx, Permission.MEMBER_INVITE);

        // Persisted membership is INVITED (no access until accepted), records who invited.
        ArgumentCaptor<Membership> mc = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(mc.capture());
        assertThat(mc.getValue().getStatus()).isEqualTo(MembershipStatus.INVITED);
        assertThat(mc.getValue().getRole()).isEqualTo(OrgRole.EDITOR);
        assertThat(mc.getValue().getInvitedByUserId()).isEqualTo(inviterId);

        // A TEAM_INVITED notification event is fired for the invitee.
        ArgumentCaptor<TeamInvitedEvent> ec = ArgumentCaptor.forClass(TeamInvitedEvent.class);
        verify(eventPublisher).publishEvent(ec.capture());
        assertThat(ec.getValue().invitedUserId()).isEqualTo(inviteeId);
        assertThat(ec.getValue().organizationId()).isEqualTo(orgId);
        assertThat(ec.getValue().invitedByName()).isEqualTo("Org Owner");
    }

    @Test
    void invite_nullRole_defaultsToMember() {
        User invitee = TestFixtures.createUser("agent@example.com");
        invitee.setId(inviteeId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(team(false)));
        when(userRepository.findByEmail("agent@example.com")).thenReturn(Optional.of(invitee));
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, inviteeId)).thenReturn(Optional.empty());
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(inviterId)).thenReturn(Optional.empty());

        var response = service.invite(ownerCtx(), new InviteMemberRequest("agent@example.com", null));

        assertThat(response.role()).isEqualTo(OrgRole.MEMBER);
    }

    @Test
    void invite_personalWorkspace_rejected() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(team(true)));

        assertThatThrownBy(() -> service.invite(ownerCtx(), new InviteMemberRequest("x@example.com", OrgRole.MEMBER)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void invite_asOwnerRole_rejected() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(team(false)));

        assertThatThrownBy(() -> service.invite(ownerCtx(), new InviteMemberRequest("x@example.com", OrgRole.OWNER)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void invite_duplicateMember_rejected() {
        User invitee = TestFixtures.createUser("dup@example.com");
        invitee.setId(inviteeId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(team(false)));
        when(userRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(invitee));
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, inviteeId))
                .thenReturn(Optional.of(Membership.builder().role(OrgRole.MEMBER).status(MembershipStatus.ACTIVE).build()));

        assertThatThrownBy(() -> service.invite(ownerCtx(), new InviteMemberRequest("dup@example.com", OrgRole.MEMBER)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void invite_unknownUser_throwsNotFound() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(team(false)));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(ownerCtx(), new InviteMemberRequest("ghost@example.com", OrgRole.MEMBER)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(membershipRepository, never()).save(any());
    }

    // ── listInvitations ────────────────────────────────────────────────────

    @Test
    void listInvitations_returnsOnlyPendingWithInviterName() {
        UUID userId = inviteeId;
        Membership pending = Membership.builder()
                .id(UUID.randomUUID()).organizationId(orgId).userId(userId)
                .role(OrgRole.EDITOR).status(MembershipStatus.INVITED).invitedByUserId(inviterId).build();
        User inviter = TestFixtures.createUser("boss@example.com");
        inviter.setId(inviterId);
        inviter.setFullName("The Boss");

        when(membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.INVITED))
                .thenReturn(List.of(pending));
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(team(false)));
        when(userRepository.findById(inviterId)).thenReturn(Optional.of(inviter));

        var result = service.listInvitations(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).organizationId()).isEqualTo(orgId);
        assertThat(result.get(0).organizationName()).isEqualTo("Acme");
        assertThat(result.get(0).role()).isEqualTo(OrgRole.EDITOR);
        assertThat(result.get(0).invitedByName()).isEqualTo("The Boss");
    }

    @Test
    void listInvitations_none_returnsEmpty() {
        when(membershipRepository.findByUserIdAndStatus(inviteeId, MembershipStatus.INVITED))
                .thenReturn(List.of());

        assertThat(service.listInvitations(inviteeId)).isEmpty();
    }

    // ── acceptInvitation ───────────────────────────────────────────────────

    @Test
    void acceptInvitation_invitedBecomesActive() {
        Membership m = Membership.builder()
                .id(UUID.randomUUID()).organizationId(orgId).userId(inviteeId)
                .role(OrgRole.MEMBER).status(MembershipStatus.INVITED).build();
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, inviteeId)).thenReturn(Optional.of(m));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(team(false)));
        when(membershipRepository.countByOrganizationId(orgId)).thenReturn(3L);

        var response = service.acceptInvitation(inviteeId, orgId);

        assertThat(m.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(response.myRole()).isEqualTo(OrgRole.MEMBER);
        assertThat(response.memberCount()).isEqualTo(3);
        verify(membershipRepository).save(m);
    }

    @Test
    void acceptInvitation_alreadyActive_rejected() {
        Membership m = Membership.builder()
                .organizationId(orgId).userId(inviteeId)
                .role(OrgRole.MEMBER).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, inviteeId)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.acceptInvitation(inviteeId, orgId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void acceptInvitation_noMembership_throwsNotFound() {
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, inviteeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvitation(inviteeId, orgId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── declineInvitation ──────────────────────────────────────────────────

    @Test
    void declineInvitation_deletesPendingMembership() {
        Membership m = Membership.builder()
                .id(UUID.randomUUID()).organizationId(orgId).userId(inviteeId)
                .role(OrgRole.MEMBER).status(MembershipStatus.INVITED).build();
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, inviteeId)).thenReturn(Optional.of(m));

        service.declineInvitation(inviteeId, orgId);

        verify(membershipRepository).delete(m);
    }

    @Test
    void declineInvitation_activeMembership_rejected() {
        Membership m = Membership.builder()
                .organizationId(orgId).userId(inviteeId)
                .role(OrgRole.MEMBER).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, inviteeId)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.declineInvitation(inviteeId, orgId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).delete(any());
    }

    // ── setRole / removeMember / leave ─────────────────────────────────────

    @Test
    void setRole_validRole_updatesAndReturns() {
        UUID targetId = UUID.randomUUID();
        Membership m = Membership.builder()
                .organizationId(orgId).userId(targetId)
                .role(OrgRole.MEMBER).status(MembershipStatus.ACTIVE).build();
        User target = TestFixtures.createUser("target@example.com");
        target.setId(targetId);
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, targetId)).thenReturn(Optional.of(m));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        var ctx = ownerCtx();
        var response = service.setRole(ctx, targetId, OrgRole.EDITOR);

        verify(orgContextService).require(ctx, Permission.MEMBER_MANAGE);
        assertThat(m.getRole()).isEqualTo(OrgRole.EDITOR);
        assertThat(response.role()).isEqualTo(OrgRole.EDITOR);
    }

    @Test
    void setRole_toOwner_rejected() {
        assertThatThrownBy(() -> service.setRole(ownerCtx(), UUID.randomUUID(), OrgRole.OWNER))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void setRole_targetIsOwner_rejected() {
        UUID targetId = UUID.randomUUID();
        Membership owner = Membership.builder()
                .organizationId(orgId).userId(targetId)
                .role(OrgRole.OWNER).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, targetId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.setRole(ownerCtx(), targetId, OrgRole.ADMIN))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void setRole_membershipNotFound_throwsNotFound() {
        UUID targetId = UUID.randomUUID();
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setRole(ownerCtx(), targetId, OrgRole.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeMember_self_rejected() {
        // ctx.userId() == inviterId → removing yourself must go through "leave", not removeMember.
        assertThatThrownBy(() -> service.removeMember(ownerCtx(), inviterId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).delete(any());
    }

    @Test
    void removeMember_owner_rejected() {
        UUID targetId = UUID.randomUUID();
        Membership owner = Membership.builder()
                .organizationId(orgId).userId(targetId)
                .role(OrgRole.OWNER).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, targetId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.removeMember(ownerCtx(), targetId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).delete(any());
    }

    @Test
    void removeMember_validMember_deletes() {
        UUID targetId = UUID.randomUUID();
        Membership m = Membership.builder()
                .organizationId(orgId).userId(targetId)
                .role(OrgRole.MEMBER).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, targetId)).thenReturn(Optional.of(m));

        var ctx = ownerCtx();
        service.removeMember(ctx, targetId);

        verify(orgContextService).require(ctx, Permission.MEMBER_MANAGE);
        verify(membershipRepository).delete(m);
    }

    @Test
    void leave_owner_rejected() {
        Membership owner = Membership.builder()
                .organizationId(orgId).userId(inviterId)
                .role(OrgRole.OWNER).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, inviterId)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.leave(ownerCtx()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).delete(any());
    }

    @Test
    void leave_member_deletes() {
        Membership m = Membership.builder()
                .organizationId(orgId).userId(inviterId)
                .role(OrgRole.MEMBER).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByOrganizationIdAndUserId(orgId, inviterId)).thenReturn(Optional.of(m));

        var ctx = new OrgContext(orgId, inviterId, UUID.randomUUID(),
                OrgRole.MEMBER, OrgRole.MEMBER.permissions(), false);
        service.leave(ctx);

        verify(membershipRepository).delete(m);
    }

    // ── create (free-org ownership cap) ────────────────────────────────────

    private Plan freePlan() {
        return Plan.builder().id(UUID.randomUUID()).name(Plan.DEFAULT_PLAN_NAME).price(BigDecimal.ZERO).build();
    }

    @Test
    void create_freePlanAtLimit_blocked() {
        when(planRepository.findByName(Plan.DEFAULT_PLAN_NAME)).thenReturn(Optional.of(freePlan()));
        when(organizationRepository.countOwnedFreeOrgs(inviterId)).thenReturn(2L);

        assertThatThrownBy(() -> service.create(inviterId, new CreateOrganizationRequest("New Org")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(organizationRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void create_freePlanBelowLimit_succeeds() {
        when(planRepository.findByName(Plan.DEFAULT_PLAN_NAME)).thenReturn(Optional.of(freePlan()));
        when(organizationRepository.countOwnedFreeOrgs(inviterId)).thenReturn(1L);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> {
            Organization o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        var response = service.create(inviterId, new CreateOrganizationRequest("New Org"));

        assertThat(response.name()).isEqualTo("New Org");
        assertThat(response.myRole()).isEqualTo(OrgRole.OWNER);
        assertThat(response.personal()).isFalse();
        verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    void create_paidDefaultPlan_skipsFreeLimit() {
        Plan paid = TestFixtures.createPlan(); // price 9.99 > 0
        when(planRepository.findByName(Plan.DEFAULT_PLAN_NAME)).thenReturn(Optional.of(paid));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> {
            Organization o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        var response = service.create(inviterId, new CreateOrganizationRequest("Paid Org"));

        assertThat(response.myRole()).isEqualTo(OrgRole.OWNER);
        // Paid orgs are unlimited → the free-org counter is never consulted.
        verify(organizationRepository, never()).countOwnedFreeOrgs(any());
    }
}
