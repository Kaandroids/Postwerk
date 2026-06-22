package com.postwerk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Detailed automation response including nodes, edges, and flow layout data. */
public record AutomationDetailResponse(
        UUID id,
        String name,
        String description,
        String type,
        String kind,
        String status,
        String color,
        String flowData,
        List<AutomationNodeDto> nodes,
        List<AutomationEdgeDto> edges,
        List<AutomationConstantDto> constants,
        Instant lastRunAt,
        boolean locked,
        Instant createdAt,
        Instant updatedAt
) {}
