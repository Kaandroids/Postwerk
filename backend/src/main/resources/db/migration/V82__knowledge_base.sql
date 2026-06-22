-- Knowledge Base (doc/KNOWLEDGE_BASE_DESIGN.md, Phase 1): org-scoped reference data that the
-- VECTOR_SEARCH automation node retrieves candidates from. The schema is borrowed from an existing
-- parameter_set; entries are filled instances embedded for hybrid (vector + full-text) retrieval.

CREATE TABLE knowledge_bases (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id          UUID NOT NULL,
    name             VARCHAR(120) NOT NULL,
    description      TEXT,
    parameter_set_id UUID NOT NULL REFERENCES parameter_sets(id),
    -- KB-level role overlay keyed by parameter-set field name:
    -- { "<field>": { "embed": <bool>, "keyword": <bool> } }
    field_roles      JSONB NOT NULL DEFAULT '{}',
    -- Optional natural-key field name for CSV upsert re-import (null -> full replace).
    unique_field     VARCHAR(100),
    locked           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);
CREATE INDEX idx_kb_org      ON knowledge_bases (organization_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_kb_paramset ON knowledge_bases (parameter_set_id);

CREATE TABLE knowledge_base_entries (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    knowledge_base_id  UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    organization_id    UUID NOT NULL,
    data               JSONB NOT NULL,
    -- 3072-dim pgvector built from the concatenated embed:true fields. No ANN index: at the
    -- expected scale (avg 5-10 entries/KB) a sequential scan is instant, and pgvector cannot
    -- HNSW-index >2000 dims anyway (see design doc D5).
    embedding          vector(3072),
    -- Concatenated keyword:true field values backing the full-text index (built by the service).
    search_text        TEXT,
    -- Hash of the embed-field text -> lets re-import skip re-embedding unchanged rows.
    content_hash       VARCHAR(64),
    embedding_dirty    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_kbe_kb    ON knowledge_base_entries (knowledge_base_id);
CREATE INDEX idx_kbe_dirty ON knowledge_base_entries (embedding_dirty) WHERE embedding_dirty = TRUE;
-- Expression GIN index for hybrid full-text search (dynamic keyword fields -> not a generated column).
CREATE INDEX idx_kbe_fts   ON knowledge_base_entries USING GIN (to_tsvector('simple', coalesce(search_text, '')));
