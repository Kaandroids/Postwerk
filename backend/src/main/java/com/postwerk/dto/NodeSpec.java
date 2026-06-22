package com.postwerk.dto;

/**
 * A node specification used to materialize a flow from a marketplace snapshot manifest (and any other
 * "create an automation flow from explicit inputs" path). {@code config} is the node's JSON config
 * (already resource-id-remapped + account-cleared by the caller).
 *
 * @since 1.0
 */
public record NodeSpec(
        String nodeType,
        String label,
        double positionX,
        double positionY,
        String config
) {}
