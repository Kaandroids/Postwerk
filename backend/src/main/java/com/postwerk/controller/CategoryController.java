package com.postwerk.controller;

import com.postwerk.dto.CategoryExportDto;
import com.postwerk.dto.CategoryRequest;
import com.postwerk.dto.CategoryResponse;
import com.postwerk.service.CategoryService;
import com.postwerk.service.OrgContextService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for email category CRUD operations with bulk import/export.
 *
 * <p>Categories define AI classification targets with descriptions, positive/negative
 * training examples, and display colors. Used by the CATEGORIZE automation node.
 * Scoped to the active organization (#4); reads require RESOURCE_VIEW, writes RESOURCE_EDIT.
 * All eight endpoints are inherited from {@link OrgScopedCrudController}.</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Email category CRUD with AI classification support")
public class CategoryController extends OrgScopedCrudController<CategoryRequest, CategoryResponse, CategoryExportDto> {

    public CategoryController(CategoryService categoryService, OrgContextService orgContext) {
        super(categoryService, orgContext);
    }
}
