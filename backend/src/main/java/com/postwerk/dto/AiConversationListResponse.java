package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/** Summary DTO for listing AI conversations. */
public record AiConversationListResponse(
        UUID id,
        String title,
        Instant updatedAt
) {}
