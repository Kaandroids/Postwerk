package com.postwerk.repository;

import com.postwerk.model.AuditAction;
import com.postwerk.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditLog} entities.
 * Provides paginated retrieval and retention-based cleanup of user audit trail records.
 *
 * @since 1.0
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Modifying
    @Query(value = "DELETE FROM audit_log WHERE id IN (SELECT id FROM audit_log WHERE created_at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    List<AuditLog> findByUserId(UUID userId);

    /** GDPR data-footprint counts. */
    long countByUserId(UUID userId);

    long countByOrganizationId(UUID organizationId);

    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<AuditLog> findByUserIdAndActionOrderByCreatedAtDesc(UUID userId, AuditAction action, Pageable pageable);

    // Org-scoped queries (multi-tenant audit). Org-wide for AUDIT_VIEW holders; the userId variants
    // restrict to a single member (a member's own entries, or an admin's member filter).
    Page<AuditLog> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<AuditLog> findByOrganizationIdAndActionOrderByCreatedAtDesc(UUID organizationId, AuditAction action, Pageable pageable);

    Page<AuditLog> findByOrganizationIdAndUserIdOrderByCreatedAtDesc(UUID organizationId, UUID userId, Pageable pageable);

    Page<AuditLog> findByOrganizationIdAndUserIdAndActionOrderByCreatedAtDesc(UUID organizationId, UUID userId, AuditAction action, Pageable pageable);

    // Admin queries — all users
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action, Pageable pageable);

    // Admin System Health "recent events" feed — latest infra-relevant audit entries.
    List<AuditLog> findTop20ByActionInOrderByCreatedAtDesc(Collection<AuditAction> actions);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId ORDER BY a.createdAt DESC")
    Page<AuditLog> findByUserIdAdmin(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.action = :action ORDER BY a.createdAt DESC")
    Page<AuditLog> findByUserIdAndActionAdmin(@Param("userId") UUID userId, @Param("action") AuditAction action, Pageable pageable);

    // Admin query with all three filters optional (null = ignored). Lets the admin audit-log endpoint
    // additionally narrow by organization (added in V71) without multiplying the finder permutations.
    @Query("SELECT a FROM AuditLog a WHERE (:userId IS NULL OR a.userId = :userId) " +
           "AND (:action IS NULL OR a.action = :action) " +
           "AND (:orgId IS NULL OR a.organizationId = :orgId) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findForAdmin(@Param("userId") UUID userId,
                                @Param("action") AuditAction action,
                                @Param("orgId") UUID orgId,
                                Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt >= :from AND a.createdAt <= :to ORDER BY a.createdAt DESC")
    Page<AuditLog> findByDateRange(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Modifying
    @Query(value = "UPDATE audit_log SET ip_address = regexp_replace(ip_address, '\\d+$', '0') " +
            "WHERE id IN (SELECT id FROM audit_log WHERE created_at < :cutoff AND ip_address IS NOT NULL " +
            "AND ip_address NOT LIKE '%x' AND ip_address NOT LIKE '%.0' LIMIT :batchSize)",
            nativeQuery = true)
    int pseudonymizeIpsBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
