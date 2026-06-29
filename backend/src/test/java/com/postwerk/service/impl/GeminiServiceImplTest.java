package com.postwerk.service.impl;

import com.postwerk.dto.AiAttachment;
import com.postwerk.service.AiUsageService;
import com.postwerk.service.PromptService;
import com.postwerk.service.QuotaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link GeminiServiceImpl} guards that run BEFORE the real Gemini client is built —
 * the AI quota pre-check and the missing-API-key guard — across all three entry points
 * (extract / classify / match). The real generate-content call needs the live SDK and is out of
 * scope here. The {@code @Value} api-key stays null in a plain unit test, firing the guard.
 */
@ExtendWith(MockitoExtension.class)
class GeminiServiceImplTest {

    @Mock private AiUsageService aiUsageService;
    @Mock private QuotaService quotaService;
    @Mock private PromptService promptService;

    private GeminiServiceImpl service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Real ObjectMapper (unused before the guards); the rest are mocks.
        service = new GeminiServiceImpl(aiUsageService, new ObjectMapper(), quotaService, promptService);
    }

    @Test
    void extract_quotaExceeded_throws_andRecordsNoUsage() {
        doThrow(new IllegalStateException("AI quota exceeded"))
                .when(quotaService).checkAiTokenQuota(orgId);

        assertThatThrownBy(() -> service.extract(orgId, userId, "email body", List.of()))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(aiUsageService);
    }

    @Test
    void extract_missingApiKey_throws() {
        assertThatThrownBy(() -> service.extract(orgId, userId, "email body", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key");

        verify(quotaService).checkAiTokenQuota(orgId);
        verifyNoInteractions(aiUsageService, promptService);
    }

    @Test
    void classify_missingApiKey_throws() {
        assertThatThrownBy(() -> service.classify(orgId, userId, "email body", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key");

        verifyNoInteractions(aiUsageService, promptService);
    }

    @Test
    void match_missingApiKey_throws() {
        assertThatThrownBy(() -> service.match(orgId, userId, "query", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key");

        verifyNoInteractions(aiUsageService, promptService);
    }

    @Test
    void buildParts_textOnly_whenNoAttachments() {
        List<Part> parts = GeminiServiceImpl.buildParts("prompt text", List.of());
        assertThat(parts).hasSize(1); // just the text prompt
    }

    @Test
    void buildParts_addsOneInlinePartPerAttachment() {
        List<Part> parts = GeminiServiceImpl.buildParts("prompt text", List.of(
                new AiAttachment("invoice.pdf", "application/pdf", new byte[]{1, 2, 3}),
                new AiAttachment("scan.png", "image/png", new byte[]{4, 5})));
        assertThat(parts).hasSize(3); // text + 2 attachments
    }
}
