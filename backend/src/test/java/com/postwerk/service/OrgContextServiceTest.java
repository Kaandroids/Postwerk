package com.postwerk.service;

import com.postwerk.model.MailboxGrant;
import com.postwerk.model.enums.OrgRole;
import com.postwerk.model.enums.Permission;
import com.postwerk.repository.MailboxGrantRepository;
import com.postwerk.repository.MembershipRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.service.OrgContextService.MailboxAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the authorization guards of {@link OrgContextService} — the security-critical
 * permission / ownership / per-mailbox checks (multi-tenant model #4). The org-resolution path is
 * left to integration tests; here the repositories are mocked and {@link OrgContext} snapshots are
 * built directly so each guard's allow/deny decision is locked in isolation.
 */
@ExtendWith(MockitoExtension.class)
class OrgContextServiceTest {

    @Mock private UserIdResolverService userIdResolver;
    @Mock private MembershipRepository membershipRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private MailboxGrantRepository mailboxGrantRepository;

    @InjectMocks
    private OrgContextService service;

    private OrgContext ctx(OrgRole role, Set<Permission> perms, boolean allMailbox, UUID membershipId) {
        return new OrgContext(UUID.randomUUID(), UUID.randomUUID(), membershipId, role, perms, allMailbox);
    }

    // ── require(permission) ──────────────────────────────────────────────

    @Test
    void require_allowsWhenPermissionPresent() {
        OrgContext ctx = ctx(OrgRole.MEMBER, Set.of(Permission.AUTOMATION_EDIT), false, UUID.randomUUID());
        assertThatCode(() -> service.require(ctx, Permission.AUTOMATION_EDIT)).doesNotThrowAnyException();
    }

    @Test
    void require_deniesWhenPermissionMissing() {
        OrgContext ctx = ctx(OrgRole.VIEWER, Set.of(Permission.AUTOMATION_VIEW), false, UUID.randomUUID());
        assertThatThrownBy(() -> service.require(ctx, Permission.AUTOMATION_EDIT))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Missing permission");
    }

    // ── requireOwner ─────────────────────────────────────────────────────

    @Test
    void requireOwner_allowsOwner() {
        OrgContext ctx = ctx(OrgRole.OWNER, Set.of(), true, UUID.randomUUID());
        assertThatCode(() -> service.requireOwner(ctx)).doesNotThrowAnyException();
    }

    @Test
    void requireOwner_deniesNonOwner() {
        OrgContext ctx = ctx(OrgRole.ADMIN, Set.of(), true, UUID.randomUUID());
        assertThatThrownBy(() -> service.requireOwner(ctx))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Owner-only");
    }

    // ── requireMailbox ───────────────────────────────────────────────────

    @Test
    void requireMailbox_ownerAdminBypassGrantLookup() {
        OrgContext ctx = ctx(OrgRole.ADMIN, Set.of(), true, UUID.randomUUID());

        assertThatCode(() -> service.requireMailbox(ctx, UUID.randomUUID(), MailboxAccess.SEND))
                .doesNotThrowAnyException();
        verifyNoInteractions(mailboxGrantRepository); // all-mailbox access short-circuits
    }

    @Test
    void requireMailbox_allowsReadWithReadGrant() {
        UUID membershipId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();
        OrgContext ctx = ctx(OrgRole.MEMBER, Set.of(), false, membershipId);
        when(mailboxGrantRepository.findByMembershipIdAndMailboxId(membershipId, mailboxId))
                .thenReturn(Optional.of(MailboxGrant.builder().canRead(true).canSend(false).build()));

        assertThatCode(() -> service.requireMailbox(ctx, mailboxId, MailboxAccess.READ))
                .doesNotThrowAnyException();
    }

    @Test
    void requireMailbox_deniesReadWithoutReadGrant() {
        UUID membershipId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();
        OrgContext ctx = ctx(OrgRole.MEMBER, Set.of(), false, membershipId);
        when(mailboxGrantRepository.findByMembershipIdAndMailboxId(membershipId, mailboxId))
                .thenReturn(Optional.of(MailboxGrant.builder().canRead(false).canSend(true).build()));

        assertThatThrownBy(() -> service.requireMailbox(ctx, mailboxId, MailboxAccess.READ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("READ");
    }

    @Test
    void requireMailbox_allowsSendWithSendGrant() {
        UUID membershipId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();
        OrgContext ctx = ctx(OrgRole.MEMBER, Set.of(), false, membershipId);
        when(mailboxGrantRepository.findByMembershipIdAndMailboxId(membershipId, mailboxId))
                .thenReturn(Optional.of(MailboxGrant.builder().canRead(true).canSend(true).build()));

        assertThatCode(() -> service.requireMailbox(ctx, mailboxId, MailboxAccess.SEND))
                .doesNotThrowAnyException();
    }

    @Test
    void requireMailbox_deniesWhenNoGrantRow() {
        UUID membershipId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();
        OrgContext ctx = ctx(OrgRole.VIEWER, Set.of(), false, membershipId);
        when(mailboxGrantRepository.findByMembershipIdAndMailboxId(membershipId, mailboxId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireMailbox(ctx, mailboxId, MailboxAccess.SEND))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── filterReadableMailboxes ──────────────────────────────────────────

    @Test
    void filterReadableMailboxes_returnsAllForAllMailboxAccess() {
        OrgContext ctx = ctx(OrgRole.OWNER, Set.of(), true, UUID.randomUUID());
        List<UUID> items = List.of(UUID.randomUUID(), UUID.randomUUID());

        assertThat(service.filterReadableMailboxes(ctx, items, Function.identity())).isEqualTo(items);
        verifyNoInteractions(mailboxGrantRepository);
    }

    @Test
    void filterReadableMailboxes_keepsOnlyGrantedReadableMailboxes() {
        UUID membershipId = UUID.randomUUID();
        UUID readable = UUID.randomUUID();
        UUID notReadable = UUID.randomUUID();
        UUID ungranted = UUID.randomUUID();
        OrgContext ctx = ctx(OrgRole.MEMBER, Set.of(), false, membershipId);
        when(mailboxGrantRepository.findByMembershipId(membershipId)).thenReturn(List.of(
                MailboxGrant.builder().mailboxId(readable).canRead(true).build(),
                MailboxGrant.builder().mailboxId(notReadable).canRead(false).build()));

        List<UUID> result = service.filterReadableMailboxes(
                ctx, List.of(readable, notReadable, ungranted), Function.identity());

        assertThat(result).containsExactly(readable);
    }
}
