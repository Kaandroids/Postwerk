package com.postwerk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base repository for org-scoped entities that carry both an {@code organizationId}
 * (the multi-tenant scoping key, #4) and a {@code userId} (the creator, retained for GDPR
 * export and marketplace clone-by-author). Centralizes the finder quartet that was otherwise
 * redeclared verbatim in {@code CategoryRepository}, {@code TemplateRepository},
 * {@code ParameterSetRepository}, and {@code AutomationRepository}.
 *
 * <p>Annotated {@code @NoRepositoryBean} so Spring Data does not instantiate it directly;
 * concrete repositories bind {@code T} and inherit the derived queries.</p>
 *
 * @param <T> the org-scoped entity type (must expose {@code id}, {@code organizationId}, {@code userId})
 */
@NoRepositoryBean
public interface OrgScopedRepository<T> extends JpaRepository<T, UUID> {

    /** All entities owned by the given organization. */
    List<T> findByOrganizationId(UUID organizationId);

    /** A single entity by id, scoped to the owning organization. */
    Optional<T> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** All entities created by the given user (creator-centric; GDPR export / clone-by-author). */
    List<T> findByUserId(UUID userId);

    /** A single entity by id, scoped to the creating user. */
    Optional<T> findByIdAndUserId(UUID id, UUID userId);
}
