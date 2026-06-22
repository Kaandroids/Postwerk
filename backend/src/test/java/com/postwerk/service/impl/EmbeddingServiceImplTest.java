package com.postwerk.service.impl;

import com.postwerk.service.AiUsageService;
import com.postwerk.service.QuotaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link EmbeddingServiceImpl} guards that run BEFORE the real Gemini client is built:
 * the AI quota pre-check and the missing-API-key guard. The actual embedding call needs the live
 * Gemini SDK and is out of scope for a unit test. In a plain (non-Spring) test the {@code @Value}
 * api-key field stays null, which exercises the "not configured" guard.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingServiceImplTest {

    @Mock private AiUsageService aiUsageService;
    @Mock private QuotaService quotaService;

    @InjectMocks
    private EmbeddingServiceImpl service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void embed_quotaExceeded_throws_andRecordsNoUsage() {
        doThrow(new IllegalStateException("AI quota exceeded"))
                .when(quotaService).checkAiTokenQuota(orgId);

        assertThatThrownBy(() -> service.embed(orgId, userId, "hello"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(aiUsageService);
    }

    @Test
    void embed_missingApiKey_throws_afterQuotaCheck() {
        // quota passes (void no-op); the @Value api key is null → "not configured" guard fires.
        assertThatThrownBy(() -> service.embed(orgId, userId, "hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key");

        verify(quotaService).checkAiTokenQuota(orgId);
        verifyNoInteractions(aiUsageService);
    }
}
