package com.postwerk.dto;

import java.util.List;
import java.util.UUID;

/** Response from a non-streaming AI chat interaction. */
public record AiChatResponse(
        UUID conversationId,
        String reply,
        List<AiToolCallDto> toolCalls,
        String phase
) {}
