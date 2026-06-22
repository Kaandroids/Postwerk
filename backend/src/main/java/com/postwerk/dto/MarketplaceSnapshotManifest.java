package com.postwerk.dto;

import java.util.List;

/**
 * The immutable, serialized content of a marketplace listing frozen at publish: the automation's
 * fields, its node/edge flow, and the full content of every referenced resource keyed by its original
 * id. Install materializes the buyer's copy from this manifest, decoupling installs from the author's
 * live (mutable/deletable) data. See {@code doc/KNOWLEDGE_BASE_DESIGN.md} Phase 0 + 6b.
 *
 * @since 1.0
 */
public record MarketplaceSnapshotManifest(
        AutomationSpec automation,
        List<NodeSpec> nodes,
        List<EdgeSpec> edges,
        List<ResourceSpec> resources
) {

    /** Frozen automation header fields. {@code constants} is the raw JSONB string (secrets preserved). */
    public record AutomationSpec(
            String name,
            String description,
            String kind,
            String color,
            String constants,
            String flowData
    ) {}

    /**
     * Frozen content of one referenced resource. A flat record with a {@code type} discriminator
     * ({@code CATEGORY} / {@code PARAMETER_SET} / {@code TEMPLATE} / {@code KNOWLEDGE_BASE}); only the
     * fields relevant to {@code type} are populated. {@code originalId} keys the old→new id remap.
     */
    public record ResourceSpec(
            String type,
            String originalId,
            String name,
            // CATEGORY
            String color,
            String description,
            String positiveExample,
            String negativeExample,
            float[] embedding,
            // PARAMETER_SET
            String parameters,
            // TEMPLATE
            String subject,
            String body,
            String params,
            String parameterSetId,
            // KNOWLEDGE_BASE
            String fieldRoles,
            String uniqueField,
            List<KbEntrySpec> entries
    ) {}

    /** Frozen content of one knowledge-base entry (only present for FULL-share KBs). */
    public record KbEntrySpec(
            String data,
            float[] embedding,
            String searchText,
            String contentHash
    ) {}
}
