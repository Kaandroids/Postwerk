package com.postwerk.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO for an automation workflow summary. */
public record AutomationResponse(
        UUID id,
        String name,
        String description,
        String type,
        String kind,
        String status,
        /** TRIGGER node mode (EMAIL/WEBHOOK/CRON/MANUAL); null for integrations / single-item responses. */
        String triggerMode,
        String color,
        int nodeCount,
        int edgeCount,
        Instant lastRunAt,
        long totalExecutions,
        long successCount,
        long failedCount,
        boolean locked,
        Instant createdAt,
        Instant updatedAt,
        TestModeStatsResponse testModeStats
) {}
