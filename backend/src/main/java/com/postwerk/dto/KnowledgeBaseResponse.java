package com.postwerk.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response view of a knowledge base, including the resolved {@code fieldRoles} overlay and the
 * current entry count. Never exposes embeddings.
 *
 * @since 1.0
 */
public record KnowledgeBaseResponse(
        UUID id,
        String name,
        String description,
        UUID parameterSetId,
        Map<String, KbFieldRole> fieldRoles,
        String uniqueField,
        long entryCount,
        boolean locked,
        Instant createdAt,
        Instant updatedAt
) {}
