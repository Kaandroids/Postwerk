package com.postwerk.dto;

import java.util.UUID;

/** DTO representing a single node in an automation flow graph. */
public record AutomationNodeDto(
        UUID id,
        String nodeType,
        String label,
        double positionX,
        double positionY,
        String config,
        String nodeKey
) {}
