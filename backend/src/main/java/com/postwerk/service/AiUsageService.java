package com.postwerk.service;

import com.postwerk.event.AiUsageRecordedEvent;
import com.postwerk.model.AiTokenUsage;
import com.postwerk.repository.AiTokenUsageRepository;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.GenerateContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for asynchronously recording AI token and embedding usage with cost tracking.
 * Persists per-call usage metrics for quota enforcement and admin analytics.
 *
 * @since 1.0
 */
@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    private final AiTokenUsageRepository repository;
    private final PricingService pricing;
    private final ApplicationEventPublisher eventPublisher;

    public AiUsageService(AiTokenUsageRepository repository, PricingService pricing,
                          ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.pricing = pricing;
        this.eventPublisher = eventPublisher;
    }

    /** Nudge the quota-notification check after a billable usage row is persisted (org-attributed only). */
    private void publishUsageRecorded(UUID organizationId) {
        if (organizationId != null) {
            eventPublisher.publishEvent(new AiUsageRecordedEvent(organizationId));
        }
    }

    /**
     * Logs a warning when usage cannot be attributed to a billing org. Such rows are still persisted
     * (so analytics keep them) but never count toward any org's monthly cost cap or the AI-limit
     * widget — a persistently-null org is a likely cause of a widget stuck at 0%.
     */
    private void warnIfUnattributed(UUID organizationId, String operation) {
        if (organizationId == null) {
            log.warn("AI usage for operation '{}' has no organizationId — it will not count toward any "
                    + "org's cost quota or the AI-limit widget", operation);
        }
    }

    @Async
    public void recordGenerateContent(UUID organizationId, UUID userId, String model, String operation,
                                       GenerateContentResponse response) {
        try {
            warnIfUnattributed(organizationId, operation);
            var usage = response.usageMetadata().orElse(null);
            int promptTokens = 0, outputTokens = 0, totalTokens = 0;
            if (usage != null) {
                promptTokens = usage.promptTokenCount().orElse(0);
                outputTokens = usage.candidatesTokenCount().orElse(0);
                totalTokens = usage.totalTokenCount().orElse(0);
            }

            int costMicros = pricing.calculateCostMicros(model, promptTokens, outputTokens);

            repository.save(AiTokenUsage.builder()
                    .userId(userId)
                    .organizationId(organizationId)
                    .model(model)
                    .operation(operation)
                    .promptTokens(promptTokens)
                    .outputTokens(outputTokens)
                    .totalTokens(totalTokens)
                    .costMicros(costMicros)
                    .build());
            publishUsageRecorded(organizationId);
        } catch (Exception e) {
            log.warn("Failed to record AI token usage: {}", e.getMessage());
        }
    }

    @Async
    public void recordEmbedding(UUID organizationId, UUID userId, String model, EmbedContentResponse response) {
        try {
            warnIfUnattributed(organizationId, "EMBED");
            int billableChars = response.metadata()
                    .flatMap(m -> m.billableCharacterCount())
                    .orElse(0);

            int costMicros = pricing.calculateCostMicros(model, billableChars, 0);

            repository.save(AiTokenUsage.builder()
                    .userId(userId)
                    .organizationId(organizationId)
                    .model(model)
                    .operation("EMBED")
                    .billableChars(billableChars)
                    .costMicros(costMicros)
                    .build());
            publishUsageRecorded(organizationId);
        } catch (Exception e) {
            log.warn("Failed to record embedding usage: {}", e.getMessage());
        }
    }
}
