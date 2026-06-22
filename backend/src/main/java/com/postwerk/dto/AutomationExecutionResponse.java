package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for an automation execution run. */
public record AutomationExecutionResponse(
        UUID id,
        String status,
        Instant triggeredAt,
        Instant completedAt,
        int processedCount,
        String errorLog
) {}
