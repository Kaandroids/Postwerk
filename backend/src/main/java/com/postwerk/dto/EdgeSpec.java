package com.postwerk.dto;

/**
 * An edge specification linking two {@link NodeSpec}s by their index in the node list, used to
 * materialize a flow from a marketplace snapshot manifest.
 *
 * @since 1.0
 */
public record EdgeSpec(
        int sourceIndex,
        String sourceHandle,
        int targetIndex,
        String targetHandle
) {}
