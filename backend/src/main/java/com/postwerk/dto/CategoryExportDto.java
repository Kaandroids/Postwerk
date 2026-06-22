package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for importing/exporting email categories in bulk.
 *
 * @param name            the category display name
 * @param color           the hex color code for visual identification
 * @param description     a brief explanation of what emails belong in this category
 * @param positiveExample example text that should match this category
 * @param negativeExample example text that should not match this category
 */
public record CategoryExportDto(
        @NotBlank(message = "Category name is required") @Size(max = 100) String name,
        @NotBlank(message = "Color is required") @Size(max = 20) String color,
        @Size(max = 1000) String description,
        @Size(max = 2000) String positiveExample,
        @Size(max = 2000) String negativeExample
) {}
