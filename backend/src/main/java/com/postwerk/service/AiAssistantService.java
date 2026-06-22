package com.postwerk.service;

import com.postwerk.dto.AiChatRequest;
import com.postwerk.dto.AiChatResponse;
import com.postwerk.dto.AiConversationDetailResponse;
import com.postwerk.dto.AiConversationListResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for the AI chat assistant that provides conversational access
 * to workspace management operations such as creating categories, filters, and automations.
 *
 * @since 1.0
 */
public interface AiAssistantService {

    AiChatResponse chat(UUID organizationId, UUID userId, AiChatRequest request, String ipAddress);

    SseEmitter chatStream(UUID organizationId, UUID userId, AiChatRequest request, String ipAddress);

    void cancelChat(UUID userId, UUID conversationId);

    List<AiConversationListResponse> listConversations(UUID organizationId, UUID userId);

    AiConversationDetailResponse getConversation(UUID organizationId, UUID userId, UUID conversationId);

    void deleteConversation(UUID organizationId, UUID userId, UUID conversationId);
}
