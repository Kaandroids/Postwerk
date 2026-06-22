package com.postwerk.dto;

import java.util.List;
import java.util.UUID;

/** Detailed AI conversation response including full message history. */
public record AiConversationDetailResponse(
        UUID id,
        String title,
        List<AiMessageDto> messages,
        String phase
) {}
