package com.postwerk.repository;

import com.postwerk.model.ParameterSet;

/**
 * Spring Data JPA repository for {@link ParameterSet} entities.
 * Provides org-scoped access (plus creator-centric finders) to reusable AI parameter configurations.
 *
 * @since 1.0
 */
public interface ParameterSetRepository extends OrgScopedRepository<ParameterSet> {
}
