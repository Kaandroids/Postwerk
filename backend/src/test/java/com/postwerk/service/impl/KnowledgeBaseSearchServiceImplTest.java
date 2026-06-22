package com.postwerk.service.impl;

import com.postwerk.dto.ClassificationResult;
import com.postwerk.dto.KbSearchResult;
import com.postwerk.model.KnowledgeBase;
import com.postwerk.model.KnowledgeBaseEntry;
import com.postwerk.model.enums.KbSearchStatus;
import com.postwerk.repository.KnowledgeBaseEntryRepository;
import com.postwerk.repository.KnowledgeBaseRepository;
import com.postwerk.service.EmbeddingService;
import com.postwerk.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KnowledgeBaseSearchServiceImpl} — the retrieve → RRF → judge → threshold
 * routing logic, with embedding + Gemini mocked. (Native SQL is covered by the repository IT.)
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseSearchServiceImplTest {

    @Mock private KnowledgeBaseRepository kbRepository;
    @Mock private KnowledgeBaseEntryRepository entryRepository;
    @Mock private EmbeddingService embeddingService;
    @Mock private GeminiService geminiService;

    private KnowledgeBaseSearchServiceImpl service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID kbId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseSearchServiceImpl(
                kbRepository, entryRepository, embeddingService, geminiService, new ObjectMapper());
    }

    @Test
    void kbNotFound_returnsError() {
        when(kbRepository.findByIdAndOrganizationId(kbId, orgId)).thenReturn(Optional.empty());
        KbSearchResult r = service.search(orgId, userId, kbId, "Logitech", 5, 90);
        assertThat(r.status()).isEqualTo(KbSearchStatus.ERROR);
    }

    @Test
    void blankQuery_returnsNotMatched() {
        when(kbRepository.findByIdAndOrganizationId(kbId, orgId)).thenReturn(Optional.of(kb()));
        KbSearchResult r = service.search(orgId, userId, kbId, "   ", 5, 90);
        assertThat(r.status()).isEqualTo(KbSearchStatus.NOT_MATCHED);
    }

    @Test
    void matchAtOrAboveThreshold_returnsMatchedWithData() throws Exception {
        stubRetrieval();
        when(geminiService.match(eq(orgId), eq(userId), anyString(), any()))
                .thenReturn(new ClassificationResult(entryId.toString(), 95, "looks right"));

        KbSearchResult r = service.search(orgId, userId, kbId, "Logitech Klavye", 5, 90);

        assertThat(r.status()).isEqualTo(KbSearchStatus.MATCHED);
        assertThat(r.confidence()).isEqualTo(95);
        assertThat(r.match()).containsEntry("kod", "4930").containsEntry("isim", "Bürobedarf");
    }

    @Test
    void confidenceBelowThreshold_returnsNotMatched() throws Exception {
        stubRetrieval();
        when(geminiService.match(eq(orgId), eq(userId), anyString(), any()))
                .thenReturn(new ClassificationResult(entryId.toString(), 60, "unsure"));

        KbSearchResult r = service.search(orgId, userId, kbId, "Logitech Klavye", 5, 90);

        assertThat(r.status()).isEqualTo(KbSearchStatus.NOT_MATCHED);
        assertThat(r.confidence()).isEqualTo(60);
    }

    @Test
    void judgeReturnsNoMatch_returnsNotMatched() throws Exception {
        stubRetrieval();
        when(geminiService.match(eq(orgId), eq(userId), anyString(), any()))
                .thenReturn(new ClassificationResult("no_match", 0, "none fit"));

        KbSearchResult r = service.search(orgId, userId, kbId, "Logitech Klavye", 5, 90);

        assertThat(r.status()).isEqualTo(KbSearchStatus.NOT_MATCHED);
    }

    @Test
    void embeddingFailure_returnsError() throws Exception {
        when(kbRepository.findByIdAndOrganizationId(kbId, orgId)).thenReturn(Optional.of(kb()));
        when(embeddingService.embed(eq(orgId), eq(userId), anyString())).thenThrow(new RuntimeException("gemini down"));

        KbSearchResult r = service.search(orgId, userId, kbId, "Logitech", 5, 90);

        assertThat(r.status()).isEqualTo(KbSearchStatus.ERROR);
    }

    // ── helpers ────────────────────────────────────────────────

    private KnowledgeBase kb() {
        return KnowledgeBase.builder().id(kbId).organizationId(orgId).userId(userId)
                .name("SKR").parameterSetId(UUID.randomUUID()).fieldRoles("{}").build();
    }

    private KnowledgeBaseEntry entry() {
        return KnowledgeBaseEntry.builder().id(entryId).knowledgeBaseId(kbId).organizationId(orgId)
                .data("{\"kod\":\"4930\",\"isim\":\"Bürobedarf\"}").build();
    }

    private void stubRetrieval() throws Exception {
        when(kbRepository.findByIdAndOrganizationId(kbId, orgId)).thenReturn(Optional.of(kb()));
        when(embeddingService.embed(eq(orgId), eq(userId), anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(entryRepository.findClosestByEmbedding(eq(kbId), anyString(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{entryId, "{}", 0.1}));
        when(entryRepository.fullTextSearch(eq(kbId), anyString(), anyInt())).thenReturn(List.of());
        when(entryRepository.findAllById(any())).thenReturn(List.of(entry()));
    }
}
