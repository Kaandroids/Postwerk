package com.postwerk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO for importing/exporting complete automation workflows in bulk.
 *
 * <p>Contains the automation metadata, node definitions (trigger, filter, extract,
 * categorize, action), edge connections, and optional serialized flow layout data
 * for the visual editor canvas.</p>
 *
 * @param name        the automation display name
 * @param description optional description of the automation's purpose
 * @param color       the hex color code for visual identification
 * @param status      the automation status (ACTIVE, PAUSED, DRAFT)
 * @param nodes       the list of automation node definitions
 * @param edges       the list of edges connecting nodes
 * @param flowData    optional serialized canvas layout for the visual editor
 * @param constants   optional user-defined constants ({{const.NAME}} placeholders)
 */
public record AutomationExportDto(
        @NotBlank(message = "Automation name is required") @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotBlank(message = "Color is required") @Size(max = 20) String color,
        @NotBlank(message = "Status is required") String status,
        @NotNull(message = "Nodes are required") List<AutomationNodeExportDto> nodes,
        @NotNull(message = "Edges are required") List<AutomationEdgeExportDto> edges,
        String flowData,
        List<AutomationConstantDto> constants
) {
    /** Represents a single automation node in an export bundle. */
    public record AutomationNodeExportDto(
            @NotBlank String tempId,
            @NotBlank String nodeType,
            String label,
            double positionX,
            double positionY,
            String config
    ) {}

    /** Represents a directed edge between two automation nodes in an export bundle. */
    public record AutomationEdgeExportDto(
            @NotBlank String sourceTempId,
            String sourceHandle,
            @NotBlank String targetTempId,
            String targetHandle
    ) {}
}
