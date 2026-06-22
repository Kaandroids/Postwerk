package com.postwerk.service;

import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.MailboxGrant;
import com.postwerk.model.Membership;
import com.postwerk.model.Organization;
import com.postwerk.model.enums.MembershipStatus;
import com.postwerk.model.enums.OrgRole;
import com.postwerk.model.enums.Permission;
import com.postwerk.repository.MailboxGrantRepository;
import com.postwerk.repository.MembershipRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.util.OrgAuditContext;
import com.postwerk.util.UuidUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the caller's {@link OrgContext} for a request (multi-tenant model #4).
 *
 * <p>The active organization travels in the {@code X-Org-Id} header (the JWT stays identity-only).
 * When the header is absent the caller's personal workspace is used. A header pointing at an
 * organization the caller is not an active member of is rejected with 403. Authorization call sites
 * check a discrete {@link Permission} via {@link #require(OrgContext, Permission)}.</p>
 *
 * @since 1.0
 */
@Service
public class OrgContextService {

    public static final String ORG_HEADER = "X-Org-Id";

    /** Per-mailbox access dimension used by {@link #requireMailbox}. */
    public enum MailboxAccess { READ, SEND }

    private final UserIdResolverService userIdResolver;
    private final MembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final MailboxGrantRepository mailboxGrantRepository;

    public OrgContextService(UserIdResolverService userIdResolver,
                             MembershipRepository membershipRepository,
                             OrganizationRepository organizationRepository,
                             MailboxGrantRepository mailboxGrantRepository) {
        this.userIdResolver = userIdResolver;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.mailboxGrantRepository = mailboxGrantRepository;
    }

    /** Resolves the active org context from the authenticated principal and the {@code X-Org-Id} header. */
    @Transactional(readOnly = true)
    public OrgContext resolve(UserDetails principal, HttpServletRequest request) {
        UUID userId = userIdResolver.resolve(principal);
        String header = request != null ? request.getHeader(ORG_HEADER) : null;
        return resolve(userId, header);
    }

    /** Resolves the active org context for a user and an optional requested org id (header value). */
    @Transactional(readOnly = true)
    public OrgContext resolve(UUID userId, String requestedOrgId) {
        Membership membership;
        if (requestedOrgId != null && !requestedOrgId.isBlank()) {
            UUID orgId = UuidUtil.parseOrThrow(requestedOrgId, "organization id");
            membership = membershipRepository.findByOrganizationIdAndUserId(orgId, userId)
                    .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                    .orElseThrow(() -> new AccessDeniedException("Not an active member of the requested organization"));
        } else {
            membership = defaultMembership(userId);
        }
        requireNotSuspended(membership.getOrganizationId());
        OrgRole role = membership.getRole();
        boolean allMailbox = role == OrgRole.OWNER || role == OrgRole.ADMIN;
        // Expose the validated active org to AuditService for the current request thread.
        OrgAuditContext.set(membership.getOrganizationId());
        return new OrgContext(membership.getOrganizationId(), userId, membership.getId(),
                role, role.permissions(), allMailbox);
    }

    /**
     * Blocks tenant access to a suspended organization (admin panel suspend/activate, #4). Platform
     * staff act on suspended orgs through the admin API, which never resolves an {@link OrgContext},
     * so they are unaffected. Personal orgs cannot be suspended, so the default path never trips here.
     */
    private void requireNotSuspended(UUID orgId) {
        organizationRepository.findById(orgId).ifPresent(org -> {
            if (org.getSuspendedAt() != null) {
                throw new AccessDeniedException("This organization is suspended");
            }
        });
    }

    /** The caller's default org: their personal workspace, else the first active membership. */
    private Membership defaultMembership(UUID userId) {
        Organization personal = organizationRepository.findByOwnerUserIdAndPersonalTrue(userId).orElse(null);
        if (personal != null) {
            var m = membershipRepository.findByOrganizationIdAndUserId(personal.getId(), userId);
            if (m.isPresent()) return m.get();
        }
        return membershipRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "for user " + userId));
    }

    /** Throws 403 unless the context grants the permission. */
    public void require(OrgContext ctx, Permission permission) {
        if (!ctx.has(permission)) {
            throw new AccessDeniedException("Missing permission: " + permission);
        }
    }

    /** Throws 403 unless the caller owns the org (owner-only actions: delete org, transfer ownership). */
    public void requireOwner(OrgContext ctx) {
        if (!ctx.isOwner()) {
            throw new AccessDeniedException("Owner-only action");
        }
    }

    /**
     * Throws 403 unless the caller may READ/SEND on the given mailbox (#4 per-mailbox grants).
     * Owner/Admin bypass via implicit all-mailbox access; Member/Viewer need a matching grant row.
     */
    @Transactional(readOnly = true)
    public void requireMailbox(OrgContext ctx, UUID mailboxId, MailboxAccess access) {
        if (ctx.allMailboxAccess()) return;
        MailboxGrant grant = mailboxGrantRepository
                .findByMembershipIdAndMailboxId(ctx.membershipId(), mailboxId).orElse(null);
        boolean ok = grant != null && (access == MailboxAccess.READ ? grant.isCanRead() : grant.isCanSend());
        if (!ok) {
            throw new AccessDeniedException("No " + access + " access to this mailbox");
        }
    }

    /**
     * Filters a list of mailbox-bearing items to those the caller may READ. Owner/Admin see all;
     * Member/Viewer see only granted mailboxes.
     */
    @Transactional(readOnly = true)
    public <T> List<T> filterReadableMailboxes(OrgContext ctx, List<T> items, Function<T, UUID> mailboxIdFn) {
        if (ctx.allMailboxAccess()) return items;
        Set<UUID> readable = mailboxGrantRepository.findByMembershipId(ctx.membershipId()).stream()
                .filter(MailboxGrant::isCanRead)
                .map(MailboxGrant::getMailboxId)
                .collect(Collectors.toSet());
        return items.stream().filter(i -> readable.contains(mailboxIdFn.apply(i))).toList();
    }
}
