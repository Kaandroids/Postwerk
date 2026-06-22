package com.postwerk.service;

import com.postwerk.dto.WizardChatRequest;
import com.postwerk.dto.WizardClaimResponse;
import com.postwerk.dto.WizardSessionResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Service for the public AI automation wizard.
 * Manages wizard sessions in Redis and orchestrates the Gemini-powered
 * chat flow for unauthenticated visitors.
 */
public interface WizardService {

    /**
     * Streams an AI chat response for the wizard via SSE.
     */
    SseEmitter chatStream(WizardChatRequest request, String ipAddress);

    /**
     * Claims a completed wizard session, creating real entities for the user.
     */
    WizardClaimResponse claimSession(UUID sessionId, UUID userId, String ipAddress);

    /**
     * Retrieves session state for frontend reconnect.
     */
    WizardSessionResponse getSession(UUID sessionId);
}
