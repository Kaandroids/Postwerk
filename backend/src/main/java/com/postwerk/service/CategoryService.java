package com.postwerk.service;

import com.postwerk.dto.CategoryExportDto;
import com.postwerk.dto.CategoryRequest;
import com.postwerk.dto.CategoryResponse;

import java.util.UUID;

/**
 * Service interface for managing email classification categories.
 * Inherits the org-scoped CRUD + import/export surface from {@link OrgScopedCrudService}.
 *
 * <p>Methods are scoped by {@code organizationId}; an {@code actingUserId} is threaded separately
 * for audit logging and AI-embedding quota attribution.</p>
 *
 * @since 1.0
 */
public interface CategoryService extends OrgScopedCrudService<CategoryRequest, CategoryResponse, CategoryExportDto> {

    /**
     * Adds a learning example to a category (#3c, from an approval-inbox correction) and re-computes
     * its embedding. The most recent ~20 examples are kept.
     */
    void addLearningExample(UUID organizationId, UUID actingUserId, UUID categoryId, String text);
}
