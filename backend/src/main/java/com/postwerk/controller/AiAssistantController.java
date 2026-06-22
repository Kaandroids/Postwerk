package com.postwerk.controller;

import com.postwerk.dto.AiChatRequest;
import com.postwerk.dto.AiChatResponse;
import com.postwerk.dto.AiConversationDetailResponse;
import com.postwerk.dto.AiConversationListResponse;
import com.postwerk.service.AiAssistantService;
import com.postwerk.service.OrgContext;
import com.postwerk.service.UserIdResolverService;
import com.postwerk.util.IpResolverUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the AI assistant chat interface.
 *
 * <p>Manages AI-powered conversations that can query, summarize, and act on the user's
 * email data. Supports multi-turn conversations with tool-calling capabilities
 * (email search, categorization, automation creation).</p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Assistant", description = "Conversational AI chat with tool-calling capabilities")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final UserIdResolverService userIdResolver;

    public AiAssistantController(AiAssistantService aiAssistantService,
                                 UserIdResolverService userIdResolver) {
        this.aiAssistantService = aiAssistantService;
        this.userIdResolver = userIdResolver;
    }

    /** Sends a chat message and returns the AI response with optional tool results. */
    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            OrgContext ctx,
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(aiAssistantService.chat(ctx.organizationId(), ctx.userId(), request, IpResolverUtil.extractIp(httpRequest)));
    }

    /** Sends a chat message and streams the AI response as SSE events. */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            OrgContext ctx,
            @Valid @RequestBody AiChatRequest request,
            HttpServletRequest httpRequest) {
        return aiAssistantService.chatStream(ctx.organizationId(), ctx.userId(), request, IpResolverUtil.extractIp(httpRequest));
    }

    /** Cancels an active streaming chat session. */
    @PostMapping("/chat/{conversationId}/cancel")
    public ResponseEntity<Void> cancelChat(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID conversationId) {
        UUID userId = userIdResolver.resolve(userDetails);
        aiAssistantService.cancelChat(userId, conversationId);
        return ResponseEntity.ok().build();
    }

    /** Lists the caller's AI conversations within the active organization. */
    @GetMapping("/conversations")
    public ResponseEntity<List<AiConversationListResponse>> listConversations(
            OrgContext ctx) {
        return ResponseEntity.ok(aiAssistantService.listConversations(ctx.organizationId(), ctx.userId()));
    }

    /** Returns a single conversation with full message history. */
    @GetMapping("/conversations/{id}")
    public ResponseEntity<AiConversationDetailResponse> getConversation(
            OrgContext ctx,
            @PathVariable UUID id) {
        return ResponseEntity.ok(aiAssistantService.getConversation(ctx.organizationId(), ctx.userId(), id));
    }

    /** Deletes a conversation and all its messages. */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(
            OrgContext ctx,
            @PathVariable UUID id) {
        aiAssistantService.deleteConversation(ctx.organizationId(), ctx.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
