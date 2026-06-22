package com.postwerk.repository;

import com.postwerk.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Plan} entities.
 * Manages subscription plans with name-based lookup and user count queries.
 *
 * @since 1.0
 */
public interface PlanRepository extends JpaRepository<Plan, UUID> {

    boolean existsByName(String name);

    Optional<Plan> findByName(String name);

    @Query(value = "SELECT COUNT(*) FROM users WHERE plan_id = :planId AND deleted_at IS NULL", nativeQuery = true)
    long countUsersByPlan(@Param("planId") UUID planId);
}
