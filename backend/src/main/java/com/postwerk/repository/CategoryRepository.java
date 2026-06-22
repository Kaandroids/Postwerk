package com.postwerk.repository;

import com.postwerk.model.Category;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Category} entities.
 * Supports org-scoped CRUD (via {@link OrgScopedRepository}) and pgvector-based
 * nearest-neighbor embedding search for email categorization.
 *
 * @since 1.0
 */
public interface CategoryRepository extends OrgScopedRepository<Category> {

    /**
     * Scalar projection over the columns consumed by {@code CategoryResponse} /
     * {@code CategoryExportDto}. Deliberately excludes the 3072-dim {@code embedding}
     * (vector, ~tens of KB/row) and {@code learnedExamples} so list/export endpoints do
     * not pay to hydrate + parse a vector that the responses never read.
     */
    interface CategoryView {
        UUID getId();
        String getName();
        String getColor();
        String getDescription();
        String getPositiveExample();
        String getNegativeExample();
        boolean isLocked();
        Instant getCreatedAt();
    }

    /** All categories owned by the org, as embedding-free scalar projections. */
    List<CategoryView> findViewByOrganizationId(UUID organizationId);

    @Query(value = """
            SELECT id, name, color, description, positive_example, negative_example
            FROM categories
            WHERE id IN (:categoryIds) AND deleted_at IS NULL AND embedding IS NOT NULL
            ORDER BY embedding <=> cast(:emailVector as vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findClosestByEmbedding(@Param("categoryIds") List<UUID> categoryIds,
                                           @Param("emailVector") String emailVector,
                                           @Param("limit") int limit);
}
