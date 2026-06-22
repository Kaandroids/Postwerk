package com.postwerk.service;

import com.postwerk.event.AiUsageRecordedEvent;
import com.postwerk.model.AiTokenUsage;
import com.postwerk.repository.AiTokenUsageRepository;
import com.google.genai.types.EmbedContentMetadata;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiUsageService} — the cost-accounting recorder that turns a Gemini response
 * into a billable {@link AiTokenUsage} row (token/char counts → {@code costMicros} via
 * {@link PricingService}) and nudges the quota-notification check. This is the heart of the
 * cost-based AI quota system; it never charges money. The Google GenAI response types are AutoValue
 * (abstract) so they mock cleanly.
 */
@ExtendWith(MockitoExtension.class)
class AiUsageServiceTest {

    @Mock private AiTokenUsageRepository repository;
    @Mock private PricingService pricing;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AiUsageService service;

    private UUID orgId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    // ── recordGenerateContent ──────────────────────────────────────────────

    @Test
    void recordGenerateContent_computesCostAndPersists() {
        var meta = mock(GenerateContentResponseUsageMetadata.class);
        when(meta.promptTokenCount()).thenReturn(Optional.of(100));
        when(meta.candidatesTokenCount()).thenReturn(Optional.of(50));
        when(meta.totalTokenCount()).thenReturn(Optional.of(150));
        var response = mock(GenerateContentResponse.class);
        when(response.usageMetadata()).thenReturn(Optional.of(meta));
        when(pricing.calculateCostMicros("gemini-2.5-flash", 100, 50)).thenReturn(1234);

        service.recordGenerateContent(orgId, userId, "gemini-2.5-flash", "EXTRACT", response);

        ArgumentCaptor<AiTokenUsage> c = ArgumentCaptor.forClass(AiTokenUsage.class);
        verify(repository).save(c.capture());
        AiTokenUsage saved = c.getValue();
        assertThat(saved.getPromptTokens()).isEqualTo(100);
        assertThat(saved.getOutputTokens()).isEqualTo(50);
        assertThat(saved.getTotalTokens()).isEqualTo(150);
        assertThat(saved.getCostMicros()).isEqualTo(1234);
        assertThat(saved.getModel()).isEqualTo("gemini-2.5-flash");
        assertThat(saved.getOperation()).isEqualTo("EXTRACT");
        assertThat(saved.getOrganizationId()).isEqualTo(orgId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        // Org-attributed usage nudges the quota-notification check.
        verify(eventPublisher).publishEvent(any(AiUsageRecordedEvent.class));
    }

    @Test
    void recordGenerateContent_noUsageMetadata_savesZeroTokens() {
        var response = mock(GenerateContentResponse.class);
        when(response.usageMetadata()).thenReturn(Optional.empty());
        when(pricing.calculateCostMicros("m", 0, 0)).thenReturn(0);

        service.recordGenerateContent(orgId, userId, "m", "CLASSIFY", response);

        ArgumentCaptor<AiTokenUsage> c = ArgumentCaptor.forClass(AiTokenUsage.class);
        verify(repository).save(c.capture());
        assertThat(c.getValue().getPromptTokens()).isZero();
        assertThat(c.getValue().getOutputTokens()).isZero();
        assertThat(c.getValue().getTotalTokens()).isZero();
        assertThat(c.getValue().getCostMicros()).isZero();
    }

    @Test
    void recordGenerateContent_nullOrg_persistsButPublishesNoEvent() {
        // Unattributed usage is still persisted for analytics, but counts toward no org's cap → no event.
        var response = mock(GenerateContentResponse.class);
        when(response.usageMetadata()).thenReturn(Optional.empty());
        when(pricing.calculateCostMicros(any(), anyInt(), anyInt())).thenReturn(0);

        service.recordGenerateContent(null, userId, "m", "EXTRACT", response);

        verify(repository).save(any(AiTokenUsage.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recordGenerateContent_persistenceFailure_isSwallowed() {
        // Recording is best-effort off the request thread — a failure must never bubble up.
        var response = mock(GenerateContentResponse.class);
        when(response.usageMetadata()).thenReturn(Optional.empty());
        when(pricing.calculateCostMicros(any(), anyInt(), anyInt())).thenReturn(0);
        doThrow(new RuntimeException("db down")).when(repository).save(any());

        assertThatCode(() -> service.recordGenerateContent(orgId, userId, "m", "EXTRACT", response))
                .doesNotThrowAnyException();
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── recordEmbedding ────────────────────────────────────────────────────

    @Test
    void recordEmbedding_computesCostFromBillableChars() {
        var meta = mock(EmbedContentMetadata.class);
        when(meta.billableCharacterCount()).thenReturn(Optional.of(200));
        var response = mock(EmbedContentResponse.class);
        when(response.metadata()).thenReturn(Optional.of(meta));
        when(pricing.calculateCostMicros("gemini-embedding-001", 200, 0)).thenReturn(99);

        service.recordEmbedding(orgId, userId, "gemini-embedding-001", response);

        ArgumentCaptor<AiTokenUsage> c = ArgumentCaptor.forClass(AiTokenUsage.class);
        verify(repository).save(c.capture());
        AiTokenUsage saved = c.getValue();
        assertThat(saved.getBillableChars()).isEqualTo(200);
        assertThat(saved.getOperation()).isEqualTo("EMBED");
        assertThat(saved.getCostMicros()).isEqualTo(99);
        assertThat(saved.getModel()).isEqualTo("gemini-embedding-001");
        verify(eventPublisher).publishEvent(any(AiUsageRecordedEvent.class));
    }

    @Test
    void recordEmbedding_noMetadata_savesZeroChars() {
        var response = mock(EmbedContentResponse.class);
        when(response.metadata()).thenReturn(Optional.empty());
        when(pricing.calculateCostMicros("m", 0, 0)).thenReturn(0);

        service.recordEmbedding(orgId, userId, "m", response);

        ArgumentCaptor<AiTokenUsage> c = ArgumentCaptor.forClass(AiTokenUsage.class);
        verify(repository).save(c.capture());
        assertThat(c.getValue().getBillableChars()).isZero();
        assertThat(c.getValue().getCostMicros()).isZero();
    }
}
