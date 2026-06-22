package com.postwerk.service;

import com.postwerk.dto.CategoryCandidate;
import com.postwerk.dto.ClassificationResult;
import com.postwerk.dto.ParameterItemDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service interface for AI-powered email processing using Google Gemini.
 * Provides structured data extraction from email content and multi-category classification.
 *
 * @since 1.0
 */
public interface GeminiService {

    Map<String, Object> extract(UUID organizationId, UUID userId, String emailText, List<ParameterItemDto> parameters) throws Exception;

    ClassificationResult classify(UUID organizationId, UUID userId, String emailText, List<CategoryCandidate> candidates) throws Exception;

    /**
     * Picks the single best-matching candidate for a free-text query (knowledge-base semantic match).
     * Mirrors {@link #classify} but is framed as reference-data matching rather than email
     * classification. The returned {@code categoryId} carries the chosen candidate's id, or
     * {@code "no_match"} when none fit.
     */
    ClassificationResult match(UUID organizationId, UUID userId, String query, List<CategoryCandidate> candidates) throws Exception;
}
