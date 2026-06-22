package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * Create/update payload for a knowledge base. {@code fieldRoles} marks which parameter-set fields
 * are embedded/keyword-indexed; the service requires at least one {@code embed:true} field.
 *
 * @since 1.0
 */
public record KnowledgeBaseRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotNull UUID parameterSetId,
        Map<String, KbFieldRole> fieldRoles,
        @Size(max = 100) String uniqueField
) {}
