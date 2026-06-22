package com.postwerk.dto;

import java.util.UUID;

/** DTO representing a directed edge between two automation nodes. */
public record AutomationEdgeDto(
        UUID id,
        UUID sourceNodeId,
        String sourceHandle,
        UUID targetNodeId,
        String targetHandle
) {}
