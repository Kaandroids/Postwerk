-- Feature flags — admin System console. Named toggles with rollout %, targeting, per-segment
-- overrides, kill-switch + change history. Flag evaluation wiring is per-feature (future) work;
-- this stores the definitions + state + targeting metadata.

CREATE TABLE IF NOT EXISTS feature_flags (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_key           VARCHAR(120) NOT NULL UNIQUE,
    name               VARCHAR(160) NOT NULL,
    description        TEXT,
    kind               VARCHAR(20)  NOT NULL DEFAULT 'RELEASE',
    enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    rollout            INTEGER      NOT NULL DEFAULT 0,
    audience           VARCHAR(20)  NOT NULL DEFAULT 'EVERYONE',
    audience_plans     VARCHAR(200),
    audience_org_id    UUID,
    audience_org_name  VARCHAR(200),
    killed             BOOLEAN      NOT NULL DEFAULT FALSE,
    archived           BOOLEAN      NOT NULL DEFAULT FALSE,
    on_since_at        TIMESTAMPTZ,
    created_by_name    VARCHAR(200),
    updated_by_name    VARCHAR(200),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_feature_flags_kind    ON feature_flags (kind);
CREATE INDEX IF NOT EXISTS idx_feature_flags_updated ON feature_flags (updated_at DESC);

CREATE TABLE IF NOT EXISTS feature_flag_overrides (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_id  UUID NOT NULL REFERENCES feature_flags (id) ON DELETE CASCADE,
    scope    VARCHAR(200) NOT NULL,
    value    VARCHAR(8)   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_feature_flag_overrides_flag ON feature_flag_overrides (flag_id);

CREATE TABLE IF NOT EXISTS feature_flag_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_id     UUID NOT NULL REFERENCES feature_flags (id) ON DELETE CASCADE,
    label       VARCHAR(300) NOT NULL,
    actor       VARCHAR(200) NOT NULL DEFAULT 'system',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_feature_flag_events_flag ON feature_flag_events (flag_id, created_at);
