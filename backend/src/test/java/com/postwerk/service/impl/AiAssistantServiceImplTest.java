package com.postwerk.service.impl;

import com.postwerk.TestFixtures;
import com.postwerk.dto.AiChatRequest;
import com.postwerk.exception.QuotaExceededException;
import com.postwerk.exception.ResourceNotFoundException;
import com.postwerk.repository.AiConversationRepository;
import com.postwerk.service.AiToolRegistry;
import com.postwerk.service.AiUsageService;
import com.postwerk.service.AuditService;
import com.postwerk.service.ConversationMessageCodec;
import com.postwerk.service.ConversationPhaseManager;
import com.postwerk.service.QuotaService;
import com.postwerk.service.SystemPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiAssistantServiceImpl}. The Gemini call goes through an internal,
 * lazily-built client (not injectable), so the deep tool-call loop needs the live SDK and is out of
 * scope. What IS unit-testable: the {@code chat()} input guards that run before the client is built
 * (message validation → quota → API key) and the conversation CRUD helpers, which never touch Gemini.
 */
@ExtendWith(MockitoExtension.class)
class AiAssistantServiceImplTest {

    @Mock private QuotaService quotaService;
    @Mock private AiConversationRepository conversationRepository;
    @Mock private AiToolRegistry toolRegistry;
    @Mock private AiUsageService aiUsageService;
    @Mock private AuditService auditService;
    @Mock private ConversationMessageCodec messageCodec;
    @Mock private ConversationPhaseManager phaseManager;
    @Mock private SystemPromptBuilder systemPromptBuilder;

    @InjectMocks
    private AiAssistantServiceImpl service;

    private UUID orgId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    // ── chat() input guards (all run before the Gemini client is built) ──────

    @Test
    void chat_blankMessage_rejected() {
        var req = new AiChatRequest("   ", null, null, null);

        assertThatThrownBy(() -> service.chat(orgId, userId, req, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(quotaService, conversationRepository, aiUsageService);
    }

    @Test
    void chat_tooLongMessage_rejected() {
        var req = new AiChatRequest("x".repeat(10_001), null, null, null);

        assertThatThrownBy(() -> service.chat(orgId, userId, req, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(conversationRepository, aiUsageService);
    }

    @Test
    void chat_quotaExceeded_throws() {
        doThrow(new QuotaExceededException("AI cost", 600, 500, "PRO"))
                .when(quotaService).checkAiTokenQuota(orgId);
        var req = new AiChatRequest("Build me an automation", null, null, null);

        assertThatThrownBy(() -> service.chat(orgId, userId, req, "127.0.0.1"))
                .isInstanceOf(QuotaExceededException.class);

        // Quota is enforced before any conversation work or AI usage.
        verifyNoInteractions(conversationRepository, aiUsageService);
    }

    @Test
    void chat_missingApiKey_throws() {
        // Valid message + quota passes (void no-op); the @Value api key is null → guard fires.
        var req = new AiChatRequest("Build me an automation", null, null, null);

        assertThatThrownBy(() -> service.chat(orgId, userId, req, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key");

        verify(quotaService).checkAiTokenQuota(orgId);
        verifyNoInteractions(conversationRepository, aiUsageService);
    }

    // ── conversation CRUD (no Gemini involved) ───────────────────────────────

    @Test
    void listConversations_mapsEntities() {
        var conv = TestFixtures.createAiConversation(userId);
        when(conversationRepository.findTop100ByUserIdAndOrganizationIdOrderByUpdatedAtDesc(userId, orgId))
                .thenReturn(List.of(conv));

        var result = service.listConversations(orgId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(conv.getId());
        assertThat(result.get(0).title()).isEqualTo(conv.getTitle());
    }

    @Test
    void getConversation_notFound_throws() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findByIdAndUserIdAndOrganizationId(convId, userId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConversation(orgId, userId, convId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getConversation_returnsDetailWithPhase() {
        var conv = TestFixtures.createAiConversation(userId); // phase OPEN, title "Test Conversation"
        when(conversationRepository.findByIdAndUserIdAndOrganizationId(conv.getId(), userId, orgId))
                .thenReturn(Optional.of(conv));
        when(messageCodec.deserialize(conv.getMessages())).thenReturn(List.of());

        var result = service.getConversation(orgId, userId, conv.getId());

        assertThat(result.id()).isEqualTo(conv.getId());
        assertThat(result.title()).isEqualTo(conv.getTitle());
        assertThat(result.phase()).isEqualTo("OPEN");
        assertThat(result.messages()).isEmpty();
    }

    @Test
    void deleteConversation_notFound_throws() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findByIdAndUserIdAndOrganizationId(convId, userId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteConversation(orgId, userId, convId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(conversationRepository, never()).save(any());
    }

    @Test
    void deleteConversation_softDeletes() {
        var conv = TestFixtures.createAiConversation(userId);
        when(conversationRepository.findByIdAndUserIdAndOrganizationId(conv.getId(), userId, orgId))
                .thenReturn(Optional.of(conv));

        service.deleteConversation(orgId, userId, conv.getId());

        assertThat(conv.getDeletedAt()).isNotNull(); // soft-delete, not a hard delete
        verify(conversationRepository).save(conv);
    }
}
