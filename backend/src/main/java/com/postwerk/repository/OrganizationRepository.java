package com.postwerk.repository;

import com.postwerk.model.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Organization} entities (multi-tenant model #4).
 *
 * @since 1.0
 */
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    /** The auto-created personal workspace for a user, if any. */
    Optional<Organization> findByOwnerUserIdAndPersonalTrue(UUID ownerUserId);

    /**
     * Count of free (Starter) organizations a user owns — used to cap how many free orgs an account
     * may own (paid orgs, price &gt; 0, are excluded and unlimited). A null plan counts as free.
     * Soft-deleted orgs are excluded via the entity's {@code @SQLRestriction}.
     */
    @Query("SELECT COUNT(o) FROM Organization o LEFT JOIN o.plan p " +
           "WHERE o.ownerUserId = :ownerUserId AND (p IS NULL OR p.price <= 0)")
    long countOwnedFreeOrgs(@Param("ownerUserId") UUID ownerUserId);

    /**
     * The personal workspaces for many users in a single query (admin user list, N+1 avoidance).
     * Plan is eager-fetched (a ToOne) so the user list can resolve each user's effective plan
     * (name + AI cap) without a per-row query.
     */
    @Query("SELECT o FROM Organization o LEFT JOIN FETCH o.plan " +
           "WHERE o.personal = true AND o.ownerUserId IN :ownerUserIds")
    List<Organization> findPersonalByOwnerUserIds(@Param("ownerUserIds") Collection<UUID> ownerUserIds);

    /**
     * Paginated org search for the admin panel: optional name substring + optional personal/team
     * filter (pass {@code null} personal to include both). Plan is eager-fetched (a ToOne, so safe
     * with pagination) to avoid an N+1 when rendering plan names; a separate count query is provided.
     */
    @Query(value = "SELECT o FROM Organization o LEFT JOIN FETCH o.plan " +
                   "WHERE (:search = '' OR LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                   "AND (:personal IS NULL OR o.personal = :personal)",
           countQuery = "SELECT COUNT(o) FROM Organization o " +
                   "WHERE (:search = '' OR LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                   "AND (:personal IS NULL OR o.personal = :personal)")
    Page<Organization> searchForAdmin(@Param("search") String search,
                                      @Param("personal") Boolean personal,
                                      Pageable pageable);

    /**
     * All organizations with their plan eager-fetched, for the admin Plans &amp; Subscriptions screen
     * (filtering/sorting/paging + derived usage-cap done in-memory, like the other admin tooling).
     */
    @Query("SELECT o FROM Organization o LEFT JOIN FETCH o.plan")
    List<Organization> findAllWithPlanForAdmin();
}
