package com.postwerk.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Request DTO for updating an automation's node graph and viewport state. */
public record FlowUpdateRequest(
        @NotNull List<FlowNodeRequest> nodes,
        @NotNull List<FlowEdgeRequest> edges,
        String viewport
) {
    public record FlowNodeRequest(
            String id,
            String nodeType,
            String label,
            double positionX,
            double positionY,
            String config,
            String nodeKey
    ) {}

    public record FlowEdgeRequest(
            String id,
            String sourceNodeId,
            String sourceHandle,
            String targetNodeId,
            String targetHandle
    ) {}
}
