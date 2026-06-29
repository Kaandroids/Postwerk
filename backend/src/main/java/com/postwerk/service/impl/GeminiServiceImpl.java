package com.postwerk.service.impl;

import com.postwerk.dto.AiAttachment;
import com.postwerk.dto.CategoryCandidate;
import com.postwerk.dto.ClassificationResult;
import com.postwerk.dto.ParameterItemDto;
import com.postwerk.service.AiUsageService;
import com.postwerk.service.GeminiService;
import com.postwerk.service.PromptService;
import com.postwerk.service.QuotaService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Default implementation of {@link GeminiService}.
 *
 * <p>Integrates with the Google Gemini generative AI API to provide structured data extraction
 * from email content and confidence-scored email classification against user-defined categories.
 * Dynamically builds JSON response schemas from parameter set definitions and records all AI
 * usage for billing and analytics.</p>
 *
 * @since 1.0
 */
@Service
public class GeminiServiceImpl implements GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiServiceImpl.class);

    /** Per-request timeout (ms) for Gemini calls — matches the AI assistant chat client. */
    private static final int GEMINI_TIMEOUT_MS = 30_000;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private final AiUsageService aiUsageService;
    private final ObjectMapper objectMapper;
    private final QuotaService quotaService;
    private final PromptService promptService;

    /**
     * Single reusable Gemini client for this service instance. The API key and httpOptions are
     * static, so a per-instance singleton is correct — building a new client on every call wastes
     * connection pools and threads. Lazily initialized (double-checked locking) and never closed:
     * a long-lived {@link Client} is fine even though it is {@link AutoCloseable}.
     */
    private volatile Client client;

    public GeminiServiceImpl(AiUsageService aiUsageService, ObjectMapper objectMapper,
                             QuotaService quotaService, PromptService promptService) {
        this.aiUsageService = aiUsageService;
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
        this.promptService = promptService;
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
    public Map<String, Object> extract(UUID organizationId, UUID userId, String emailText,
                                       List<ParameterItemDto> parameters, List<AiAttachment> attachments) throws Exception {
        quotaService.checkAiTokenQuota(organizationId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        Client client = client();

        Schema responseSchema = buildSchema(parameters);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();

        String prompt = "Extract structured data from this email. Return only valid JSON.\n\n" + emailText;

        GenerateContentResponse response = generate(client, prompt, attachments, config);
        aiUsageService.recordGenerateContent(organizationId, userId, model, "EXTRACT", response);

        String json = response.text();
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    @Override
    @Retry(name = "gemini")
    @CircuitBreaker(name = "gemini")
    public ClassificationResult classify(UUID organizationId, UUID userId, String emailText,
                                         List<CategoryCandidate> candidates, List<AiAttachment> attachments) throws Exception {
        quotaService.checkAiTokenQuota(organizationId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        Client client = client();

        Schema responseSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "categoryId", Schema.builder()
                                .type("STRING")
                                .description("The UUID of the best matching category, or 'uncategorized' if none match")
                                .build(),
                        "confidence", Schema.builder()
                                .type("NUMBER")
                                .description("Confidence score from 0 to 100 indicating how well the email matches the selected category")
                                .build(),
                        "reason", Schema.builder()
                                .type("STRING")
                                .description("Brief explanation of why this category was selected")
                                .build()
                ))
                .required(List.of("categoryId", "confidence", "reason"))
                .build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();

        String prompt = promptService.load("classify.txt", Map.of(
                "emailText", emailText,
                "categories", candidateBlock(candidates)
        ));

        GenerateContentResponse response = generate(client, prompt, attachments, config);
        aiUsageService.recordGenerateContent(organizationId, userId, model, "CLASSIFY", response);

        String json = response.text();
        Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});

        String categoryId = (String) result.getOrDefault("categoryId", "uncategorized");
        int confidence = result.containsKey("confidence")
                ? ((Number) result.get("confidence")).intValue() : 0;
        String reason = (String) result.getOrDefault("reason", "");

        return new ClassificationResult(categoryId, confidence, reason);
    }

    @Override
    @Retry(name = "gemini")
    @CircuitBreaker(name = "gemini")
    public ClassificationResult match(UUID organizationId, UUID userId, String query, List<CategoryCandidate> candidates) throws Exception {
        quotaService.checkAiTokenQuota(organizationId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        Client client = client();

        Schema responseSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "matchedId", Schema.builder()
                                .type("STRING")
                                .description("The ID of the single best-matching candidate entry, or 'no_match' if none fit")
                                .build(),
                        "confidence", Schema.builder()
                                .type("NUMBER")
                                .description("Confidence score from 0 to 100 that the chosen entry is the correct match")
                                .build(),
                        "reason", Schema.builder()
                                .type("STRING")
                                .description("Brief explanation of the choice")
                                .build()
                ))
                .required(List.of("matchedId", "confidence", "reason"))
                .build();

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();

        String prompt = promptService.load("kb-judge.txt", Map.of(
                "query", query == null ? "" : query,
                "candidates", candidateBlock(candidates)
        ));

        GenerateContentResponse response = client.models.generateContent(model, prompt, config);
        aiUsageService.recordGenerateContent(organizationId, userId, model, "KB_MATCH", response);

        String json = response.text();
        Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});

        String matchedId = (String) result.getOrDefault("matchedId", "no_match");
        int confidence = result.containsKey("confidence")
                ? ((Number) result.get("confidence")).intValue() : 0;
        String reason = (String) result.getOrDefault("reason", "");

        return new ClassificationResult(matchedId, confidence, reason);
    }

    /**
     * Issues a {@code generateContent} call, attaching any inline attachment {@link Part}s alongside the
     * text prompt. With no attachments this is the plain text-prompt call (unchanged behaviour);
     * otherwise the prompt and each attachment become parts of a single multimodal {@code user}
     * {@link Content}. Gemini counts the attachment tokens in the returned usage metadata, so billing
     * is recorded as before by the callers.
     */
    private GenerateContentResponse generate(Client client, String prompt,
                                             List<AiAttachment> attachments, GenerateContentConfig config) {
        if (attachments == null || attachments.isEmpty()) {
            return client.models.generateContent(model, prompt, config);
        }
        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(prompt));
        for (AiAttachment a : attachments) {
            parts.add(Part.fromBytes(a.data(), a.mimeType()));
        }
        Content content = Content.builder().role("user").parts(parts).build();
        return client.models.generateContent(model, List.of(content), config);
    }

    /** Renders candidates as an {@code ID/Name/Description (+examples)} block for the classify/match prompts. */
    private String candidateBlock(List<CategoryCandidate> candidates) {
        StringBuilder block = new StringBuilder();
        for (CategoryCandidate c : candidates) {
            block.append("- ID: ").append(c.id()).append("\n");
            block.append("  Name: ").append(c.name()).append("\n");
            if (c.description() != null && !c.description().isBlank()) {
                block.append("  Description: ").append(c.description()).append("\n");
            }
            if (c.positiveExample() != null && !c.positiveExample().isBlank()) {
                block.append("  Positive example (items like this MATCH): ").append(c.positiveExample()).append("\n");
            }
            if (c.negativeExample() != null && !c.negativeExample().isBlank()) {
                block.append("  Negative example (items like this do NOT match): ").append(c.negativeExample()).append("\n");
            }
        }
        return block.toString();
    }

    private Schema buildSchema(List<ParameterItemDto> parameters) {
        Map<String, Schema> properties = new LinkedHashMap<>();
        List<String> requiredFields = new ArrayList<>();

        for (ParameterItemDto param : parameters) {
            Schema fieldSchema = buildFieldSchema(param);
            if (param.isList()) {
                fieldSchema = Schema.builder()
                        .type("ARRAY")
                        .items(fieldSchema)
                        .build();
            }
            properties.put(param.name(), fieldSchema);
            if (param.required()) {
                requiredFields.add(param.name());
            }
        }

        Schema.Builder builder = Schema.builder()
                .type("OBJECT")
                .properties(properties);
        if (!requiredFields.isEmpty()) {
            builder.required(requiredFields);
        }
        return builder.build();
    }

    private Schema buildFieldSchema(ParameterItemDto param) {
        String description = buildDescription(param);

        if ("OBJECT".equals(param.type()) && param.children() != null && !param.children().isEmpty()) {
            Map<String, Schema> childProps = new LinkedHashMap<>();
            List<String> childRequired = new ArrayList<>();
            for (ParameterItemDto child : param.children()) {
                Schema childSchema = buildFieldSchema(child);
                if (child.isList()) {
                    childSchema = Schema.builder()
                            .type("ARRAY")
                            .items(childSchema)
                            .build();
                }
                childProps.put(child.name(), childSchema);
                if (child.required()) {
                    childRequired.add(child.name());
                }
            }
            Schema.Builder builder = Schema.builder()
                    .type("OBJECT")
                    .properties(childProps);
            if (!description.isEmpty()) {
                builder.description(description);
            }
            if (!childRequired.isEmpty()) {
                builder.required(childRequired);
            }
            return builder.build();
        }

        String schemaType = mapType(param.type());
        Schema.Builder builder = Schema.builder().type(schemaType);
        if (!description.isEmpty()) {
            builder.description(description);
        }
        return builder.build();
    }

    private String mapType(String paramType) {
        return switch (paramType) {
            case "NUMBER" -> "NUMBER";
            case "BOOLEAN" -> "BOOLEAN";
            default -> "STRING"; // TEXT, EMAIL, DATE
        };
    }

    private String buildDescription(ParameterItemDto param) {
        StringBuilder sb = new StringBuilder();
        if (param.description() != null && !param.description().isBlank()) {
            sb.append(param.description());
        }
        if ("DATE".equals(param.type())) {
            if (!sb.isEmpty()) sb.append(". ");
            sb.append("Format: ISO 8601 date (YYYY-MM-DD)");
        }
        if (param.positiveExample() != null && !param.positiveExample().isBlank()) {
            if (!sb.isEmpty()) sb.append(". ");
            sb.append("Example: ").append(param.positiveExample());
        }
        if (param.negativeExample() != null && !param.negativeExample().isBlank()) {
            if (!sb.isEmpty()) sb.append(". ");
            sb.append("Not like: ").append(param.negativeExample());
        }
        return sb.toString();
    }
}
