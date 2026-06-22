package com.postwerk.repository;

import com.postwerk.model.PendingAction;
import com.postwerk.model.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for supervised-mode pending actions (the approval inbox).
 *
 * @since 1.0
 */
public interface PendingActionRepository extends JpaRepository<PendingAction, UUID> {

    Page<PendingAction> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<PendingAction> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, ApprovalStatus status, Pageable pageable);

    Optional<PendingAction> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, ApprovalStatus status);

    /** Platform-wide count by status — feeds the admin System Health "job queue depth" metric. */
    long countByStatus(ApprovalStatus status);

    // ── Analytics (#analytics): approval throughput + SLA over a time window. ──

    /** Count of approvals decided into a terminal status within the window — throughput. */
    long countByOrganizationIdAndStatusAndDecidedAtAfter(UUID organizationId, ApprovalStatus status, Instant decidedAt);

    /** Average minutes from request to decision for approvals decided since a cutoff; null if none. */
    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (decided_at - created_at)) / 60.0) " +
           "FROM pending_actions WHERE organization_id = :org AND decided_at IS NOT NULL AND decided_at >= :since",
           nativeQuery = true)
    Double avgDecisionMinutesSince(@Param("org") UUID org, @Param("since") Instant since);
}
