package com.postwerk.service.impl;

import com.postwerk.dto.CategoryCandidate;
import com.postwerk.dto.ClassificationResult;
import com.postwerk.dto.KbSearchResult;
import com.postwerk.model.KnowledgeBase;
import com.postwerk.model.KnowledgeBaseEntry;
import com.postwerk.model.enums.KbSearchStatus;
import com.postwerk.repository.KnowledgeBaseEntryRepository;
import com.postwerk.repository.KnowledgeBaseRepository;
import com.postwerk.service.EmbeddingService;
import com.postwerk.service.GeminiService;
import com.postwerk.service.KnowledgeBaseSearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link KnowledgeBaseSearchService}.
 *
 * <p>Pipeline: embed the query → pgvector nearest-neighbour + Postgres full-text → reciprocal rank
 * fusion → shortlist top-K → Gemini judge picks the best candidate with a confidence → threshold gate.
 * Empty KB / no candidates short-circuits to {@code NOT_MATCHED} without an LLM call; any failure
 * yields {@code ERROR}. Embedding + judge cost is attributed to the KB's org/user via quota.</p>
 *
 * @since 1.0
 */
@Service
public class KnowledgeBaseSearchServiceImpl implements KnowledgeBaseSearchService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseSearchServiceImpl.class);

    /** Reciprocal-rank-fusion damping constant (standard default). */
    private static final double RRF_K = 60.0;
    private static final int MAX_TOP_K = 10;
    private static final int MAX_FETCH = 50;

    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeBaseEntryRepository entryRepository;
    private final EmbeddingService embeddingService;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    public KnowledgeBaseSearchServiceImpl(KnowledgeBaseRepository kbRepository,
                                          KnowledgeBaseEntryRepository entryRepository,
                                          EmbeddingService embeddingService,
                                          GeminiService geminiService,
                                          ObjectMapper objectMapper) {
        this.kbRepository = kbRepository;
        this.entryRepository = entryRepository;
        this.embeddingService = embeddingService;
        this.geminiService = geminiService;
        this.objectMapper = objectMapper;
    }

    @Override
    public KbSearchResult search(UUID organizationId, UUID userId, UUID knowledgeBaseId,
                                 String queryText, int topK, int confidenceThreshold) {
        try {
            KnowledgeBase kb = kbRepository.findByIdAndOrganizationId(knowledgeBaseId, organizationId).orElse(null);
            if (kb == null) {
                return new KbSearchResult(KbSearchStatus.ERROR, Map.of(), 0, "Knowledge base not found", 0);
            }
            if (queryText == null || queryText.isBlank()) {
                return new KbSearchResult(KbSearchStatus.NOT_MATCHED, Map.of(), 0, "Empty query", 0);
            }

            int k = Math.max(1, Math.min(topK, MAX_TOP_K));
            int fetch = Math.min(MAX_FETCH, Math.max(k * 2, k));

            float[] queryVector = embeddingService.embed(organizationId, userId, queryText);
            String vectorStr = formatVector(queryVector);

            List<Object[]> vectorRows = entryRepository.findClosestByEmbedding(knowledgeBaseId, vectorStr, fetch);
            List<Object[]> textRows = entryRepository.fullTextSearch(knowledgeBaseId, queryText, fetch);

            // Reciprocal rank fusion of the two ranked lists (rank position only; lists are pre-ordered).
            LinkedHashMap<UUID, Double> fused = new LinkedHashMap<>();
            fuse(fused, vectorRows);
            fuse(fused, textRows);
            if (fused.isEmpty()) {
                return new KbSearchResult(KbSearchStatus.NOT_MATCHED, Map.of(), 0, "No candidates", 0);
            }

            List<UUID> topIds = fused.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(k)
                    .map(Map.Entry::getKey)
                    .toList();

            Map<UUID, KnowledgeBaseEntry> byId = entryRepository.findAllById(topIds).stream()
                    .collect(Collectors.toMap(KnowledgeBaseEntry::getId, e -> e));
            List<KnowledgeBaseEntry> candidates = topIds.stream().map(byId::get).filter(Objects::nonNull).toList();
            if (candidates.isEmpty()) {
                return new KbSearchResult(KbSearchStatus.NOT_MATCHED, Map.of(), 0, "No candidates", 0);
            }

            List<CategoryCandidate> judgeCandidates = candidates.stream()
                    .map(e -> new CategoryCandidate(e.getId(), label(kb, e), renderContent(e), null, null))
                    .toList();

            ClassificationResult verdict = geminiService.match(organizationId, userId, queryText, judgeCandidates);
            UUID matchedId = parseUuidOrNull(verdict.categoryId());

            if (matchedId != null && verdict.confidence() >= confidenceThreshold) {
                KnowledgeBaseEntry matched = byId.get(matchedId);
                if (matched != null) {
                    return new KbSearchResult(KbSearchStatus.MATCHED, parseData(matched.getData()),
                            verdict.confidence(), verdict.reason(), candidates.size());
                }
            }
            return new KbSearchResult(KbSearchStatus.NOT_MATCHED, Map.of(),
                    verdict.confidence(), verdict.reason(), candidates.size());

        } catch (Exception e) {
            log.warn("KB search failed for kb {}: {}", knowledgeBaseId, e.getMessage());
            return new KbSearchResult(KbSearchStatus.ERROR, Map.of(), 0,
                    e.getMessage() == null ? "Search failed" : e.getMessage(), 0);
        }
    }

    private void fuse(Map<UUID, Double> accumulator, List<Object[]> rows) {
        for (int i = 0; i < rows.size(); i++) {
            UUID id = idOf(rows.get(i)[0]);
            if (id != null) {
                accumulator.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            }
        }
    }

    /** A short human label for a candidate — the unique-field value if set, else the first field value. */
    private String label(KnowledgeBase kb, KnowledgeBaseEntry entry) {
        Map<String, Object> data = parseData(entry.getData());
        if (kb.getUniqueField() != null && data.get(kb.getUniqueField()) != null) {
            return String.valueOf(data.get(kb.getUniqueField()));
        }
        return data.values().stream().findFirst().map(String::valueOf).orElse(entry.getId().toString());
    }

    /** Renders an entry's fields as {@code field: value} lines for the judge prompt. */
    private String renderContent(KnowledgeBaseEntry entry) {
        Map<String, Object> data = parseData(entry.getData());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append(": ").append(e.getValue());
        }
        return sb.toString();
    }

    private Map<String, Object> parseData(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static UUID idOf(Object value) {
        if (value instanceof UUID u) return u;
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null; // "no_match" or any non-UUID
        }
    }

    private static String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
