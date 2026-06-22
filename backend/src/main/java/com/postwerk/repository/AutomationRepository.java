package com.postwerk.repository;

import com.postwerk.model.Automation;
import com.postwerk.model.enums.AutomationStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Automation} entities.
 * Provides user-scoped CRUD, due-automation scheduling queries, and account-based active automation lookups.
 *
 * @since 1.0
 */
public interface AutomationRepository extends OrgScopedRepository<Automation> {

    // Organization-scoped extras beyond the inherited finder quartet (#4).
    List<Automation> findTop200ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    // User-centric extras (by creator) — for GDPR export and executor integration resolution
    // (IntegrationCallNodeExecutor); kept alongside the inherited org-scoped methods.
    List<Automation> findTop200ByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT a FROM Automation a WHERE a.status IN :statuses " +
           "AND (a.lastRunAt IS NULL OR a.lastRunAt < :cutoff)")
    List<Automation> findDueAutomations(@Param("statuses") List<AutomationStatus> statuses,
                                        @Param("cutoff") Instant cutoff);

    @Query(value = "SELECT * FROM automations WHERE deleted_at IS NULL " +
           "AND status = 'ACTIVE' AND :accountId = ANY(account_ids)",
           nativeQuery = true)
    List<Automation> findActiveByAccountId(@Param("accountId") UUID accountId);

    @Query(value = "SELECT * FROM automations WHERE deleted_at IS NULL " +
           "AND status IN ('ACTIVE', 'TESTING') AND :accountId = ANY(account_ids)",
           nativeQuery = true)
    List<Automation> findProcessableByAccountId(@Param("accountId") UUID accountId);

    @Query(value = "SELECT COUNT(*) FROM automations WHERE deleted_at IS NULL AND status = 'ACTIVE'", nativeQuery = true)
    long countActive();

    @Query(value = "SELECT COUNT(*) FROM automations WHERE deleted_at IS NULL", nativeQuery = true)
    long countAll();

    long countByUserId(UUID userId);

    // Batched count keyed by userId — avoids N+1 in admin user listings (one row per user that
    // owns at least one automation; users with none are absent and default to 0).
    @Query("SELECT a.userId, COUNT(a) FROM Automation a WHERE a.userId IN :userIds GROUP BY a.userId")
    List<Object[]> countByUserIdIn(@Param("userIds") Collection<UUID> userIds);

    // Batched count keyed by orgId — avoids N+1 in the admin subscriptions list.
    @Query("SELECT a.organizationId, COUNT(a) FROM Automation a WHERE a.organizationId IN :orgIds GROUP BY a.organizationId")
    List<Object[]> countByOrganizationIdIn(@Param("orgIds") Collection<UUID> orgIds);
}
