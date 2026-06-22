package com.postwerk.repository;

import com.postwerk.model.Template;

/**
 * Spring Data JPA repository for {@link Template} entities.
 * Provides org-scoped access (plus creator-centric finders) to reusable email reply and action templates.
 *
 * @since 1.0
 */
public interface TemplateRepository extends OrgScopedRepository<Template> {
}
