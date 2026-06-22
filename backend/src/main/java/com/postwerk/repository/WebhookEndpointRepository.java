package com.postwerk.repository;

import com.postwerk.model.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WebhookEndpoint} entities.
 * Provides token-based lookup for public ingress and org-scoped management access (#4).
 *
 * @since 1.0
 */
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    Optional<WebhookEndpoint> findByTokenAndActiveTrue(String token);

    Optional<WebhookEndpoint> findByIdAndUserId(UUID id, UUID userId);

    /** Org-scoped management lookup (#4): any member of the owning org may manage the endpoint. */
    Optional<WebhookEndpoint> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<WebhookEndpoint> findByAutomationId(UUID automationId);

    long countByUserIdAndActiveTrue(UUID userId);

    // Org-scoped (#4): inbound-webhook quota is enforced per-organization.
    long countByOrganizationIdAndActiveTrue(UUID organizationId);
}
