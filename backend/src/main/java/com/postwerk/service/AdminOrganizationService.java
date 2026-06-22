package com.postwerk.service;

import com.postwerk.dto.admin.AdminAutomationSummaryResponse;
import com.postwerk.dto.admin.AdminMailboxResponse;
import com.postwerk.dto.admin.AdminOrgDetailResponse;
import com.postwerk.dto.admin.AdminOrgResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Platform-staff oversight of customer organizations (tenants): listing, detail, ownership transfer
 * and soft-deletion. Separate from the tenant-facing {@link OrganizationService} (which is scoped to
 * the caller's own org) — this operates across ALL organizations and is gated by {@code ORG_*}
 * staff permissions.
 *
 * @since 1.0
 */
public interface AdminOrganizationService {

    Page<AdminOrgResponse> listOrganizations(String search, Boolean personal, Pageable pageable);

    AdminOrgDetailResponse getOrganization(UUID orgId);

    /** Automations owned by the organization (admin org-detail "Automations" tab). Mirrors {@code automationCount}'s ownership. */
    List<AdminAutomationSummaryResponse> getOrganizationAutomations(UUID orgId);

    /** Email accounts owned by the organization (admin org-detail "Mailboxes" tab). Mirrors {@code mailboxCount}'s ownership. */
    List<AdminMailboxResponse> getOrganizationMailboxes(UUID orgId);

    /** Transfers ownership to an existing member (new owner → OWNER, previous owner → ADMIN). */
    AdminOrgDetailResponse transferOwnership(UUID orgId, UUID newOwnerUserId);

    /**
     * Suspends a team organization: members are blocked from accessing it (their {@code X-Org-Id}
     * requests are rejected) until reactivated. Personal organizations cannot be suspended.
     *
     * @param reason optional staff note recorded for support/audit (may be {@code null}/blank)
     */
    AdminOrgDetailResponse suspendOrganization(UUID orgId, String reason);

    /** Lifts a suspension, restoring tenant access. No-op if the organization is already active. */
    AdminOrgDetailResponse activateOrganization(UUID orgId);

    /** Soft-deletes a team organization. Personal organizations cannot be deleted. */
    void deleteOrganization(UUID orgId);
}
