package com.postwerk.service.impl;

import com.postwerk.dto.CategoryExportDto;
import com.postwerk.dto.CategoryRequest;
import com.postwerk.dto.CategoryResponse;
import com.postwerk.dto.ImportResultDto;
import com.postwerk.model.AuditAction;
import com.postwerk.model.Category;
import com.postwerk.repository.CategoryRepository;
import com.postwerk.service.AuditService;
import com.postwerk.service.CategoryService;
import com.postwerk.service.EmbeddingService;
import com.postwerk.util.ImportHelper;
import com.postwerk.util.RepositoryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of {@link CategoryService}.
 *
 * <p>Manages the full lifecycle of email classification categories, including CRUD operations,
 * vector embedding generation for semantic matching, and bulk import/export with audit logging.
 * Scoped by {@code organizationId} (#4); {@code actingUserId} is used for audit + embedding quota.</p>
 *
 * @since 1.0
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryServiceImpl.class);

    private final CategoryRepository repository;
    private final AuditService auditService;
    private final EmbeddingService embeddingService;

    public CategoryServiceImpl(CategoryRepository repository, AuditService auditService,
                                EmbeddingService embeddingService) {
        this.repository = repository;
        this.auditService = auditService;
        this.embeddingService = embeddingService;
    }

    @Override
    @Transactional
    public CategoryResponse create(UUID organizationId, UUID actingUserId, CategoryRequest request, String ipAddress) {
        var category = Category.builder()
                .userId(actingUserId)
                .organizationId(organizationId)
                .name(request.name())
                .color(request.color())
                .description(request.description())
                .positiveExample(request.positiveExample())
                .negativeExample(request.negativeExample())
                .build();

        computeEmbedding(actingUserId, category);
        var saved = repository.save(category);
        auditService.log(actingUserId, AuditAction.CATEGORY_CREATED, "Category: " + saved.getName(), ipAddress);
        return toResponse(saved);
    }

    @Override
    public List<CategoryResponse> listByOrg(UUID organizationId) {
        // Use the embedding-free scalar projection — the response never reads the 3072-dim vector.
        return repository.findViewByOrganizationId(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CategoryResponse getById(UUID organizationId, UUID categoryId) {
        return toResponse(findByOrgAndId(organizationId, categoryId));
    }

    @Override
    public CategoryResponse update(UUID organizationId, UUID actingUserId, UUID categoryId, CategoryRequest request, String ipAddress) {
        // NOT method-level @Transactional: the load + save each run in their own short transaction
        // (Spring Data default), so the blocking embedding call below holds NO pooled DB connection.
        var category = findByOrgAndId(organizationId, categoryId);

        Map<String, Object> before = Map.of(
                "name", category.getName(),
                "color", category.getColor() != null ? category.getColor() : "",
                "description", category.getDescription() != null ? category.getDescription() : "",
                "positiveExample", category.getPositiveExample() != null ? category.getPositiveExample() : "",
                "negativeExample", category.getNegativeExample() != null ? category.getNegativeExample() : "");

        category.setName(request.name());
        category.setColor(request.color());
        category.setDescription(request.description());
        category.setPositiveExample(request.positiveExample());
        category.setNegativeExample(request.negativeExample());

        // Compute the embedding over the updated text (+ existing learned examples) with no connection held.
        String text = buildEmbeddingText(request.name(), request.description(),
                request.positiveExample(), request.negativeExample(), category.getLearnedExamples());
        float[] vector = safeEmbed(organizationId, actingUserId, text);
        if (vector != null) {
            category.setEmbedding(vector);
        }

        var saved = repository.save(category);

        Map<String, Object> after = Map.of(
                "name", saved.getName(),
                "color", saved.getColor() != null ? saved.getColor() : "",
                "description", saved.getDescription() != null ? saved.getDescription() : "",
                "positiveExample", saved.getPositiveExample() != null ? saved.getPositiveExample() : "",
                "negativeExample", saved.getNegativeExample() != null ? saved.getNegativeExample() : "");

        auditService.logDiff(actingUserId, AuditAction.CATEGORY_UPDATED, new LinkedHashMap<>(before), new LinkedHashMap<>(after),
                "Category: " + saved.getName(), ipAddress);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID organizationId, UUID actingUserId, UUID categoryId, String ipAddress) {
        var category = findByOrgAndId(organizationId, categoryId);
        repository.delete(category);
        auditService.log(actingUserId, AuditAction.CATEGORY_DELETED, "Category: " + category.getName(), ipAddress);
    }

    @Override
    public List<CategoryExportDto> exportAll(UUID organizationId) {
        // Use the embedding-free scalar projection — the export DTO never reads the vector.
        return repository.findViewByOrganizationId(organizationId).stream()
                .map(c -> new CategoryExportDto(
                        c.getName(), c.getColor(), c.getDescription(),
                        c.getPositiveExample(), c.getNegativeExample()))
                .toList();
    }

    @Override
    @Transactional
    public ImportResultDto importAll(UUID organizationId, UUID actingUserId, List<CategoryExportDto> items, String ipAddress) {
        return ImportHelper.runImport(items, CategoryExportDto::name, item -> {
            var request = new CategoryRequest(item.name(), item.color(), item.description(),
                    item.positiveExample(), item.negativeExample());
            create(organizationId, actingUserId, request, ipAddress);
        });
    }

    private Category findByOrgAndId(UUID organizationId, UUID categoryId) {
        return RepositoryHelper.findOrThrow(repository::findByIdAndOrganizationId, categoryId, organizationId, "Category");
    }

    private void computeEmbedding(UUID actingUserId, Category category) {
        String text = buildEmbeddingText(category.getName(), category.getDescription(),
                category.getPositiveExample(), category.getNegativeExample(), category.getLearnedExamples());
        float[] vector = safeEmbed(category.getOrganizationId(), actingUserId, text);
        if (vector != null) {
            category.setEmbedding(vector);
        }
    }

    /** Builds the embedding source text from the category's textual fields (pure; no I/O). */
    private static String buildEmbeddingText(String name, String description,
                                             String positiveExample, String negativeExample, String learnedExamples) {
        return name + " " + description
                + (positiveExample != null ? " " + positiveExample : "")
                + (negativeExample != null ? " " + negativeExample : "")
                + (learnedExamples != null ? " " + learnedExamples : "");
    }

    /**
     * Computes an embedding, swallowing failures (AI disabled / quota exhausted / API error) so
     * category CRUD still succeeds. Returns {@code null} when no vector could be produced — callers
     * must NOT be inside a transaction that holds a pooled DB connection while invoking this, since
     * the underlying call is a blocking remote (Gemini) request.
     */
    private float[] safeEmbed(UUID organizationId, UUID actingUserId, String text) {
        try {
            return embeddingService.embed(organizationId, actingUserId, text);
        } catch (Exception e) {
            log.warn("Failed to compute embedding for organization {}: {}", organizationId, e.getMessage());
            return null;
        }
    }

    @Override
    public void addLearningExample(UUID organizationId, UUID actingUserId, UUID categoryId, String text) {
        if (text == null || text.isBlank()) return;
        // NOT method-level @Transactional: the load + save each run in their own short transaction
        // (Spring Data default), so the blocking embedding call below holds NO pooled DB connection.
        Category category = findByOrgAndId(organizationId, categoryId);

        String snippet = text.strip();
        if (snippet.length() > 500) snippet = snippet.substring(0, 500);

        // Keep the most recent ~20 examples to bound the embedding input.
        String existing = category.getLearnedExamples();
        List<String> examples = (existing == null || existing.isBlank())
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(existing.split("\n")));
        examples.add(snippet);
        while (examples.size() > 20) examples.remove(0);

        category.setLearnedExamples(String.join("\n", examples));

        // Compute the embedding over the updated text with no connection held.
        String embeddingText = buildEmbeddingText(category.getName(), category.getDescription(),
                category.getPositiveExample(), category.getNegativeExample(), category.getLearnedExamples());
        float[] vector = safeEmbed(organizationId, actingUserId, embeddingText);
        if (vector != null) {
            category.setEmbedding(vector);
        }

        repository.save(category);
        log.info("Added a learning example to category {} ({} total)", category.getName(), examples.size());
    }

    @Override
    @Transactional
    public CategoryResponse toggleLock(UUID organizationId, UUID categoryId) {
        var category = findByOrgAndId(organizationId, categoryId);
        category.setLocked(!category.isLocked());
        return toResponse(repository.save(category));
    }

    @Override
    public boolean isLocked(UUID organizationId, UUID categoryId) {
        return findByOrgAndId(organizationId, categoryId).isLocked();
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(), category.getName(), category.getColor(),
                category.getDescription(), category.getPositiveExample(),
                category.getNegativeExample(), category.isLocked(), category.getCreatedAt()
        );
    }

    private CategoryResponse toResponse(CategoryRepository.CategoryView view) {
        return new CategoryResponse(
                view.getId(), view.getName(), view.getColor(),
                view.getDescription(), view.getPositiveExample(),
                view.getNegativeExample(), view.isLocked(), view.getCreatedAt()
        );
    }
}
