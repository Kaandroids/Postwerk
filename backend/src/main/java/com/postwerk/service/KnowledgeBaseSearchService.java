package com.postwerk.service;

import com.postwerk.dto.KbSearchResult;

import java.util.UUID;

/**
 * Runtime hybrid retrieval + LLM judge over a knowledge base, used by the {@code VECTOR_SEARCH}
 * automation node. Combines pgvector cosine similarity and Postgres full-text (reciprocal rank
 * fusion) to shortlist candidates, then asks Gemini to pick the best match and routes on a
 * confidence threshold. See {@code doc/KNOWLEDGE_BASE_DESIGN.md} (Phase 3).
 *
 * @since 1.0
 */
public interface KnowledgeBaseSearchService {

    /**
     * @param organizationId   owning org (KB scope + embedding/judge quota attribution)
     * @param userId           acting user (quota attribution)
     * @param knowledgeBaseId  the KB to search
     * @param queryText        the resolved query (e.g. an extracted field value)
     * @param topK             max candidates to shortlist for the judge (1–10)
     * @param confidenceThreshold minimum judge confidence (0–100) to count as a match
     */
    KbSearchResult search(UUID organizationId, UUID userId, UUID knowledgeBaseId,
                          String queryText, int topK, int confidenceThreshold);
}
