package com.postwerk.service.impl;

import com.postwerk.dto.admin.AdminAutomationSummaryResponse;
import com.postwerk.dto.admin.AdminMailboxResponse;
import com.postwerk.dto.admin.AdminOrgDetailResponse;
import com.postwerk.dto.admin.AdminOrgMemberResponse;
import com.postwerk.dto.admin.AdminOrgResponse;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.model.Membership;
import com.postwerk.model.Organization;
import com.postwerk.model.User;
import com.postwerk.model.enums.MembershipStatus;
import com.postwerk.model.enums.OrgRole;
import com.postwerk.repository.AiTokenUsageRepository;
import com.postwerk.repository.AutomationRepository;
import com.postwerk.repository.EmailAccountRepository;
import com.postwerk.repository.MembershipRepository;
import com.postwerk.repository.OrganizationRepository;
import com.postwerk.repository.UserRepository;
import com.postwerk.service.AdminOrganizationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link AdminOrganizationService}. Aggregates are batch-loaded in the list
 * path to avoid N+1; the single-org detail path reads counts directly.
 *
 * @since 1.0
 */
@Service
public class AdminOrganizationServiceImpl implements AdminOrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final AutomationRepository automationRepository;
    private final AiTokenUsageRepository aiTokenUsageRepository;

    public AdminOrganizationServiceImpl(OrganizationRepository organizationRepository,
                                        MembershipRepository membershipRepository,
                                        UserRepository userRepository,
                                        EmailAccountRepository emailAccountRepository,
                                        AutomationRepository automationRepository,
                                        AiTokenUsageRepository aiTokenUsageRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.automationRepository = automationRepository;
        this.aiTokenUsageRepository = aiTokenUsageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrgResponse> listOrganizations(String search, Boolean personal, Pageable pageable) {
        Page<Organization> page = organizationRepository.searchForAdmin(search == null ? "" : search, personal, pageable);
        List<Organization> orgs = page.getContent();

        Map<UUID, User> ownersById = new HashMap<>();
        Map<UUID, Long> memberCounts = new HashMap<>();
        if (!orgs.isEmpty()) {
            Set<UUID> ownerIds = orgs.stream()
                    .map(Organization::getOwnerUserId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!ownerIds.isEmpty()) {
                userRepository.findAllById(ownerIds).forEach(u -> ownersById.put(u.getId(), u));
            }
            List<UUID> orgIds = orgs.stream().map(Organization::getId).toList();
            for (Object[] row : membershipRepository.countMembersByOrgIds(orgIds)) {
                memberCounts.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }

        return page.map(org -> {
            User owner = ownersById.get(org.getOwnerUserId());
            return new AdminOrgResponse(
                    org.getId(), org.getName(), org.getSlug(), org.isPersonal(),
                    org.getOwnerUserId(),
                    owner != null ? owner.getEmail() : null,
                    owner != null ? owner.getFullName() : null,
                    org.getPlan() != null ? org.getPlan().getName() : null,
                    memberCounts.getOrDefault(org.getId(), 0L),
                    org.getCreatedAt(),
                    org.getSuspendedAt());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrgDetailResponse getOrganization(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId.toString()));

        List<Membership> memberships = membershipRepository.findByOrganizationId(orgId);
        List<UUID> userIds = memberships.stream().map(Membership::getUserId).toList();
        Map<UUID, User> usersById = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> usersById.put(u.getId(), u));
        }

        List<AdminOrgMemberResponse> members = memberships.stream()
                .map(m -> {
                    User u = usersById.get(m.getUserId());
                    if (u == null) return null; // soft-deleted user
                    return new AdminOrgMemberResponse(u.getId(), u.getEmail(), u.getFullName(),
                            m.getRole().name(), m.getStatus().name(), m.getCreatedAt());
                })
                .filter(Objects::nonNull)
                .toList();

        User owner = org.getOwnerUserId() == null ? null
                : usersById.computeIfAbsent(org.getOwnerUserId(),
                        id -> userRepository.findById(id).orElse(null));

        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long aiCost = aiTokenUsageRepository.sumCostMicrosByOrganizationSince(orgId, monthStart);

        return new AdminOrgDetailResponse(
                org.getId(), org.getName(), org.getSlug(), org.isPersonal(),
                org.getOwnerUserId(),
                owner != null ? owner.getEmail() : null,
                owner != null ? owner.getFullName() : null,
                org.getPlan() != null ? org.getPlan().getId() : null,
                org.getPlan() != null ? org.getPlan().getName() : null,
                members.size(),
                emailAccountRepository.countByOrganizationId(orgId),
                automationRepository.countByOrganizationId(orgId),
                aiCost,
                org.getCreatedAt(),
                org.getSuspendedAt(),
                org.getSuspensionReason(),
                members);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminAutomationSummaryResponse> getOrganizationAutomations(UUID orgId) {
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization", orgId.toString());
        }
        // Ownership mirrors mailboxCount/automationCount: the detail count is
        // automationRepository.countByOrganizationId(orgId), so the list uses the matching org-scoped
        // finder (capped at 200, newest first) to stay consistent with that count.
        return automationRepository.findTop200ByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .map(a -> new AdminAutomationSummaryResponse(
                        a.getId(), a.getName(), a.getStatus().name(), a.getKind().name(),
                        a.getCreatedAt(), a.getLastRunAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminMailboxResponse> getOrganizationMailboxes(UUID orgId) {
        if (!organizationRepository.existsById(orgId)) {
            throw new ResourceNotFoundException("Organization", orgId.toString());
        }
        // Ownership mirrors the detail's mailboxCount (emailAccountRepository.countByOrganizationId),
        // so the list uses findByOrganizationId(orgId). Reuses AdminServiceImpl's safe (secret-free)
        // mapper so the user-mailboxes and org-mailboxes tabs render identically.
        return emailAccountRepository.findByOrganizationId(orgId).stream()
                .map(AdminServiceImpl::toMailboxResponse)
                .toList();
    }

    @Override
    @Transactional
    public AdminOrgDetailResponse transferOwnership(UUID orgId, UUID newOwnerUserId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId.toString()));
        if (org.isPersonal()) {
            throw new IllegalArgumentException("Cannot transfer ownership of a personal organization");
        }
        Membership newOwner = membershipRepository.findByOrganizationIdAndUserId(orgId, newOwnerUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "The new owner must be an existing member of the organization"));

        UUID previousOwnerId = org.getOwnerUserId();
        if (previousOwnerId != null && !previousOwnerId.equals(newOwnerUserId)) {
            membershipRepository.findByOrganizationIdAndUserId(orgId, previousOwnerId)
                    .ifPresent(prev -> {
                        prev.setRole(OrgRole.ADMIN);
                        membershipRepository.save(prev);
                    });
        }
        newOwner.setRole(OrgRole.OWNER);
        newOwner.setStatus(MembershipStatus.ACTIVE);
        membershipRepository.save(newOwner);

        org.setOwnerUserId(newOwnerUserId);
        organizationRepository.save(org);

        return getOrganization(orgId);
    }

    @Override
    @Transactional
    public AdminOrgDetailResponse suspendOrganization(UUID orgId, String reason) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId.toString()));
        if (org.isPersonal()) {
            throw new IllegalArgumentException("Cannot suspend a personal organization");
        }
        if (org.getSuspendedAt() == null) {
            org.setSuspendedAt(Instant.now());
        }
        org.setSuspensionReason(reason == null || reason.isBlank() ? null : reason.trim());
        organizationRepository.save(org);
        return getOrganization(orgId);
    }

    @Override
    @Transactional
    public AdminOrgDetailResponse activateOrganization(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId.toString()));
        org.setSuspendedAt(null);
        org.setSuspensionReason(null);
        organizationRepository.save(org);
        return getOrganization(orgId);
    }

    @Override
    @Transactional
    public void deleteOrganization(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId.toString()));
        if (org.isPersonal()) {
            throw new IllegalArgumentException("Cannot delete a personal organization");
        }
        org.setDeletedAt(Instant.now());
        organizationRepository.save(org);
    }
}
