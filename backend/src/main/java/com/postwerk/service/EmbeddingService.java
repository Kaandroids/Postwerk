package com.postwerk.service;

import java.util.UUID;

/**
 * Service interface for generating vector embeddings from text content.
 * Used for semantic similarity search and AI-powered email classification.
 *
 * @since 1.0
 */
public interface EmbeddingService {

    float[] embed(UUID organizationId, UUID userId, String text) throws Exception;
}
