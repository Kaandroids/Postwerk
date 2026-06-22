package com.postwerk.repository;

import com.postwerk.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.postwerk.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User} entities.
 * Supports lookup by email, existence checks, and scheduled hard-deletion of soft-deleted records.
 *
 * @since 1.0
 */
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Modifying
    @Query(value = "DELETE FROM users WHERE id IN (SELECT id FROM users WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int hardDeleteSoftDeletedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    // Admin queries
    @Query(value = "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL", nativeQuery = true)
    long countActive();

    @Query(value = "SELECT COUNT(*) FROM users WHERE deleted_at IS NOT NULL", nativeQuery = true)
    long countDeleted();

    @Query(value = "SELECT COUNT(*) FROM users WHERE created_at >= :since AND deleted_at IS NULL", nativeQuery = true)
    long countCreatedSince(@Param("since") Instant since);

    // The optional plan filter joins on the user's plan_id (kept in sync with the personal org's plan by
    // AdminService.assignPlan); an empty :plan disables the filter. Matched case-insensitively by plan name.
    @Query(value = "SELECT u.* FROM users u LEFT JOIN plans p ON p.id = u.plan_id WHERE u.deleted_at IS NULL " +
            "AND (LOWER(u.full_name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "AND (:plan = '' OR LOWER(p.name) = LOWER(:plan))",
            countQuery = "SELECT COUNT(*) FROM users u LEFT JOIN plans p ON p.id = u.plan_id WHERE u.deleted_at IS NULL " +
            "AND (LOWER(u.full_name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "AND (:plan = '' OR LOWER(p.name) = LOWER(:plan))",
            nativeQuery = true)
    Page<User> searchUsers(@Param("search") String search, @Param("plan") String plan, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u LEFT JOIN plans p ON p.id = u.plan_id WHERE u.deleted_at IS NULL AND u.role = :role " +
            "AND (LOWER(u.full_name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "AND (:plan = '' OR LOWER(p.name) = LOWER(:plan))",
            countQuery = "SELECT COUNT(*) FROM users u LEFT JOIN plans p ON p.id = u.plan_id WHERE u.deleted_at IS NULL AND u.role = :role " +
            "AND (LOWER(u.full_name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "AND (:plan = '' OR LOWER(p.name) = LOWER(:plan))",
            nativeQuery = true)
    Page<User> searchUsersByRole(@Param("search") String search, @Param("role") String role, @Param("plan") String plan, Pageable pageable);

    @Query(value = "SELECT u.* FROM users u LEFT JOIN plans p ON p.id = u.plan_id WHERE u.deleted_at IS NOT NULL " +
            "AND (LOWER(u.full_name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "AND (:plan = '' OR LOWER(p.name) = LOWER(:plan))",
            countQuery = "SELECT COUNT(*) FROM users u LEFT JOIN plans p ON p.id = u.plan_id WHERE u.deleted_at IS NOT NULL " +
            "AND (LOWER(u.full_name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "AND (:plan = '' OR LOWER(p.name) = LOWER(:plan))",
            nativeQuery = true)
    Page<User> searchDeletedUsers(@Param("search") String search, @Param("plan") String plan, Pageable pageable);

    /** Staff roster — every user that currently holds a staff role (low volume; filtered in-memory). */
    List<User> findByStaffRoleIsNotNull();

    /** Grant-access candidates — non-staff, non-deleted users matching the search term. */
    @Query(value = "SELECT u.* FROM users u WHERE u.deleted_at IS NULL AND u.staff_role IS NULL " +
            "AND (LOWER(u.full_name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%'))) " +
            "ORDER BY u.full_name LIMIT :max", nativeQuery = true)
    List<User> searchNonStaff(@Param("search") String search, @Param("max") int max);

    @Modifying
    @Query(value = "UPDATE users SET last_login_ip = regexp_replace(last_login_ip, '\\d+$', '0') " +
            "WHERE id IN (SELECT id FROM users WHERE last_login_at < :cutoff AND last_login_ip IS NOT NULL " +
            "AND last_login_ip NOT LIKE '%x' AND last_login_ip NOT LIKE '%.0' LIMIT :batchSize)",
            nativeQuery = true)
    int pseudonymizeLoginIpsBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
