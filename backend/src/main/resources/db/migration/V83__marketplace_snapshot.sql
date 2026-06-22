-- Marketplace publish-time immutable snapshot (doc/KNOWLEDGE_BASE_DESIGN.md Phase 0 + 6b).
-- A listing freezes its automation + referenced resources at publish into a manifest; install
-- materializes from the manifest, decoupling installs from the author's evolving/deleted live data.
-- Backward compatible: listings without a snapshot row fall back to the live-read path.

ALTER TABLE marketplace_listings ADD COLUMN source_automation_id UUID;
-- Per-referenced-KB entry-sharing policy: { "<kbId>": "SCHEMA_ONLY" | "FULL" }.
ALTER TABLE marketplace_listings ADD COLUMN kb_share_policy JSONB NOT NULL DEFAULT '{}';

CREATE TABLE marketplace_listing_snapshots (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id   UUID NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,
    version      VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    -- Frozen automation + nodes + edges + referenced resources, serialized at publish.
    manifest     JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_listing_snapshot ON marketplace_listing_snapshots (listing_id);

-- KB content-hiding for PRIVATE-listing installs (mirrors automations.hidden). `locked` already
-- exists from V82; add only `hidden` (the get/entries API refuses to return data when hidden).
ALTER TABLE knowledge_bases ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
