package com.postwerk.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response payload for wizard session state (for frontend reconnect).
 *
 * @param sessionId      The session UUID
 * @param phase          Current phase: chatting, building, or ready
 * @param messages       Conversation message history
 * @param automationPlan Structured automation plan (nodes/edges) if available
 */
public record WizardSessionResponse(
        UUID sessionId,
        String phase,
        List<Map<String, Object>> messages,
        Map<String, Object> automationPlan
) {}
