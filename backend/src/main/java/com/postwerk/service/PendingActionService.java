package com.postwerk.service;

import com.postwerk.dto.automation.PendingActionResponse;
import com.postwerk.model.AutomationNode;
import com.postwerk.model.enums.ApprovalStatus;
import com.postwerk.service.executor.ExecutionContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

/**
 * Supervised mode (#3a): manages {@code PendingAction}s — side effects parked for human approval
 * instead of being performed during a live automation run.
 *
 * @since 1.0
 */
public interface PendingActionService {

    /**
     * Parks an action node's already-resolved side effect for approval. Called by the executor when
     * a live run reaches an {@code ACTION} node whose {@code executionMode} is {@code REVIEW}.
     *
     * @param node    the action node
     * @param detail  the resolved payload (the detail map the dry-run pass produced)
     * @param context the live execution context (source email/account)
     * @param userId  the automation owner
     */
    void park(AutomationNode node, Map<String, Object> detail, ExecutionContext context, UUID userId);

    /** The organization's approval inbox, optionally filtered by status (null = all), newest first. */
    Page<PendingActionResponse> list(UUID organizationId, ApprovalStatus status, Pageable pageable);

    /** Count of the organization's actions still awaiting a decision. */
    long countPending(UUID organizationId);

    /** Approves a pending action and performs its parked side effect ({@code deciderUserId} = approver). */
    PendingActionResponse approve(UUID organizationId, UUID deciderUserId, UUID id);

    /** Rejects a pending action; its side effect is never performed. */
    PendingActionResponse reject(UUID organizationId, UUID deciderUserId, UUID id, String note);

    /**
     * #3c: corrects the categorization that drove this action — teaches {@code correctCategoryId} the
     * action's source email (as a learning example, re-embedding the category) and rejects the action.
     */
    PendingActionResponse reclassify(UUID organizationId, UUID userId, UUID id, UUID correctCategoryId);
}
