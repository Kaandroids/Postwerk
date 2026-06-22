package com.postwerk.service;

import com.postwerk.dto.admin.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for platform administration, including user management, role assignment,
 * AI usage analytics, automation execution monitoring, audit log access, and plan CRUD operations.
 *
 * @since 1.0
 */
public interface AdminService {

    AdminStatsResponse getStats();

    Page<AdminUserResponse> getUsers(String search, String role, String status, String plan, Pageable pageable);

    AdminUserResponse getUser(UUID userId);

    AdminUserResponse updateRole(UUID userId, String role, String currentUserEmail);

    /** Assigns (or clears, when {@code staffRole} is null/blank) a user's platform staff role. */
    AdminUserResponse updateStaffRole(UUID userId, String staffRole, String currentUserEmail);

    /** The signed-in staffer's own identity + effective platform capabilities (for the admin UI to gate on). */
    StaffIdentityResponse getStaffIdentity(String email);

    void disableUser(UUID userId);

    AiUsageStatsResponse getAiUsageStats();

    List<AiUsageByUserResponse> getAiUsageByUser();

    List<TimelineDataPoint> getAiUsageTimeline(String period);

    AutomationStatsResponse getAutomationStats();

    Page<AutomationExecutionAdminResponse> getAutomationExecutions(Pageable pageable);

    /** Paged audit log with optional user, action and organization filters (any may be {@code null}). */
    Page<AdminAuditLogResponse> getAuditLog(UUID userId, String action, UUID organizationId, Pageable pageable);

    byte[] exportAuditLogCsv(UUID userId, String action);

    // ─── User detail tabs ────────────────────────────────────────────

    /** Organizations the given user is a member of (admin user-detail "Organizations" tab). */
    List<AdminUserOrgResponse> getUserOrganizations(UUID userId);

    /** The user's email accounts (admin user-detail "Mailboxes" tab). Mirrors {@code emailAccountCount}'s ownership. */
    List<AdminMailboxResponse> getUserMailboxes(UUID userId);

    // ─── Users support tooling ───────────────────────────────────────

    /** Internal staff-only notes about the target user, newest first (404 if the user doesn't exist). */
    List<StaffNoteResponse> listUserNotes(UUID targetUserId);

    /**
     * Adds an internal staff note about the target user, snapshotting the author's name/email so the
     * note survives the author's account deletion.
     *
     * @param targetUserId the user the note is about (404 if not found)
     * @param authorUserId the staff user writing the note (looked up to snapshot name/email)
     * @param body         the note text
     */
    StaffNoteResponse addUserNote(UUID targetUserId, UUID authorUserId, String body);

    /**
     * Deletes a staff note. Allowed only if the requester is the note's author OR holds USER_MANAGE;
     * otherwise throws {@link org.springframework.security.access.AccessDeniedException} (→ 403).
     *
     * @param noteId                the note to delete (404 if not found)
     * @param requesterUserId       the calling staff user's id
     * @param requesterHasUserManage whether the caller holds the USER_MANAGE authority
     */
    void deleteUserNote(UUID noteId, UUID requesterUserId, boolean requesterHasUserManage);

    /** Triggers the normal password-reset flow for the target user (404 if not found). */
    void forcePasswordReset(UUID targetUserId, String ipAddress);

    /** Active session (refresh-token) count for the target user (404 if not found). */
    AdminUserSessionsResponse getUserSessions(UUID targetUserId);

    /** Revokes all of the target user's sessions, forcing logout (404 if not found). Returns count 0. */
    AdminUserSessionsResponse revokeUserSessions(UUID targetUserId);

    // Plan CRUD
    List<PlanResponse> getPlans();

    PlanResponse createPlan(PlanRequest request);

    PlanResponse updatePlan(UUID planId, PlanRequest request);

    void deletePlan(UUID planId);

    AdminUserResponse assignPlan(UUID userId, UUID planId);

    record AutomationExecutionAdminResponse(
            UUID id,
            UUID automationId,
            String automationName,
            String status,
            int processedCount,
            String errorLog,
            java.time.Instant triggeredAt,
            java.time.Instant completedAt
    ) {}
}
