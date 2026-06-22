package com.postwerk.repository;

import com.postwerk.model.KnowledgeBase;

/**
 * Spring Data JPA repository for {@link KnowledgeBase} entities.
 * Org-scoped CRUD via {@link OrgScopedRepository} (the {@code findBy[Id]AndOrganizationId/UserId}
 * finder quartet).
 *
 * @since 1.0
 */
public interface KnowledgeBaseRepository extends OrgScopedRepository<KnowledgeBase> {
}
