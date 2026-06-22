package com.postwerk.repository;

import com.postwerk.model.Secret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Secret} entities.
 * Provides user-scoped access to encrypted secret values (API keys, tokens).
 *
 * @since 1.0
 */
public interface SecretRepository extends JpaRepository<Secret, UUID> {

    List<Secret> findAllByOrganizationId(UUID organizationId);

    Optional<Secret> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndName(UUID organizationId, String name);

    // User-centric lookup — executor fallback when the run has no org set (legacy/edge paths).
    Optional<Secret> findByIdAndUserId(UUID id, UUID userId);
}
