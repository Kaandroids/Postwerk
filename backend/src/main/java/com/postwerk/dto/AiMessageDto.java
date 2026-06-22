package com.postwerk.dto;

import java.time.Instant;
import java.util.List;

/** DTO representing a single message in an AI conversation. */
public record AiMessageDto(
        String role,
        String content,
        Instant timestamp,
        List<AiToolCallDto> toolCalls
) {}
