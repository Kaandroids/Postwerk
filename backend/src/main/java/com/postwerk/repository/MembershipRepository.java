package com.postwerk.repository;

import com.postwerk.model.Membership;
import com.postwerk.model.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Membership} entities (multi-tenant model #4).
 *
 * @since 1.0
 */
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    /** All organizations the user belongs to (any status). */
    List<Membership> findByUserId(UUID userId);

    /** The user's memberships in a given status (e.g. INVITED for pending invitations). */
    List<Membership> findByUserIdAndStatus(UUID userId, MembershipStatus status);

    /** All members of an organization. */
    List<Membership> findByOrganizationId(UUID organizationId);

    /** The caller's membership in a specific organization (used to resolve org context + permissions). */
    Optional<Membership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    long countByOrganizationId(UUID organizationId);

    /** Member counts for many orgs in a single query (admin list, N+1 avoidance). Each row is [orgId, count]. */
    @Query("SELECT m.organizationId, COUNT(m) FROM Membership m WHERE m.organizationId IN :ids GROUP BY m.organizationId")
    List<Object[]> countMembersByOrgIds(@Param("ids") Collection<UUID> ids);

    /**
     * Active-membership counts for many users in a single query (admin user list, N+1 avoidance).
     * Each row is [userId, count]; users with no active membership are absent and default to 0.
     */
    @Query("SELECT m.userId, COUNT(m) FROM Membership m " +
           "WHERE m.userId IN :ids AND m.status = com.postwerk.model.enums.MembershipStatus.ACTIVE " +
           "GROUP BY m.userId")
    List<Object[]> countActiveMembershipsByUserIds(@Param("ids") Collection<UUID> ids);

    /**
     * User ids of the org's active owners/admins — the escalation set for org-scoped notifications
     * (notification system; see doc/NOTIFICATION_SYSTEM_DESIGN.md).
     */
    @Query("SELECT m.userId FROM Membership m WHERE m.organizationId = :orgId " +
           "AND m.status = com.postwerk.model.enums.MembershipStatus.ACTIVE " +
           "AND m.role IN (com.postwerk.model.enums.OrgRole.OWNER, com.postwerk.model.enums.OrgRole.ADMIN)")
    List<UUID> findActiveAdminUserIds(@Param("orgId") UUID orgId);

    /** Active members of an org holding a specific role (NOTIFY automation node recipient resolution). */
    @Query("SELECT m.userId FROM Membership m WHERE m.organizationId = :orgId " +
           "AND m.status = com.postwerk.model.enums.MembershipStatus.ACTIVE AND m.role = :role")
    List<UUID> findActiveUserIdsByOrgAndRole(@Param("orgId") UUID orgId,
                                             @Param("role") com.postwerk.model.enums.OrgRole role);
}
