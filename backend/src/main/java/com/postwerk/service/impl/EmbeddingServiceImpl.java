package com.postwerk.service.impl;

import com.postwerk.service.AiUsageService;
import com.postwerk.service.EmbeddingService;
import com.postwerk.service.QuotaService;
import com.google.genai.Client;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.HttpOptions;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link EmbeddingService}.
 *
 * <p>Generates vector embeddings for text content using the Gemini embedding model
 * and records usage metrics through the AI usage tracking subsystem.</p>
 *
 * @since 1.0
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    /** Per-request timeout (ms) for Gemini calls — matches {@code GeminiServiceImpl}. */
    private static final int GEMINI_TIMEOUT_MS = 30_000;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.embedding-model:gemini-embedding-001}")
    private String embeddingModel;

    private final AiUsageService aiUsageService;
    private final QuotaService quotaService;

    /**
     * Single reusable Gemini client for this service instance. The API key and httpOptions are
     * static, so a per-instance singleton is correct. Lazily initialized (double-checked locking)
     * and never closed: a long-lived {@link Client} is fine even though it is {@link AutoCloseable}.
     */
    private volatile Client client;

    public EmbeddingServiceImpl(AiUsageService aiUsageService, QuotaService quotaService) {
        this.aiUsageService = aiUsageService;
        this.quotaService = quotaService;
    }

    /**
     * Returns the shared Gemini client, building it on first use with an explicit per-request
     * timeout so a hung call cannot block indefinitely.
     */
    private Client client() {
        Client c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = Client.builder()
                            .apiKey(apiKey)
                            .httpOptions(HttpOptions.builder().timeout(GEMINI_TIMEOUT_MS).build())
                            .build();
                    client = c;
                }
            }
        }
        return c;
    }

    @Override
    @Retry(name = "gemini")
    @CircuitBreaker(name = "gemini")
    public float[] embed(UUID organizationId, UUID userId, String text) throws Exception {
        quotaService.checkAiTokenQuota(organizationId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        Client client = client();
        EmbedContentResponse response = client.models.embedContent(embeddingModel, text, null);

        aiUsageService.recordEmbedding(organizationId, userId, embeddingModel, response);

        List<Float> values = response.embeddings().orElseThrow().get(0).values().orElseThrow();
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
