package com.postwerk.dto;

/**
 * A single node chip in a PUBLIC listing's node-flow preview.
 *
 * @param nodeType the node type name (e.g. {@code TRIGGER}, {@code CATEGORIZE})
 * @param label    the node's display label
 */
public record NodeChipDto(
        String nodeType,
        String label
) {}
