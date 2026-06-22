-- Platform announcements (info / success / warning / maintenance banners) — admin System console.
-- Bilingual DE+EN content, targeting, scheduling, lifecycle + change history.

CREATE TABLE IF NOT EXISTS announcements (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title_de           VARCHAR(200) NOT NULL,
    title_en           VARCHAR(200) NOT NULL,
    body_de            TEXT,
    body_en            TEXT,
    cta_label_de       VARCHAR(120),
    cta_label_en       VARCHAR(120),
    cta_url            VARCHAR(2048),
    type               VARCHAR(20)  NOT NULL,
    placement          VARCHAR(20)  NOT NULL DEFAULT 'BANNER',
    audience           VARCHAR(20)  NOT NULL DEFAULT 'EVERYONE',
    audience_plans     VARCHAR(200),
    audience_org_id    UUID,
    audience_org_name  VARCHAR(200),
    dismissible        BOOLEAN      NOT NULL DEFAULT TRUE,
    lifecycle          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    starts_at          TIMESTAMPTZ,
    ends_at            TIMESTAMPTZ,
    created_by_user_id UUID,
    created_by_name    VARCHAR(200),
    updated_by_name    VARCHAR(200),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_announcements_lifecycle ON announcements (lifecycle);
CREATE INDEX IF NOT EXISTS idx_announcements_window     ON announcements (starts_at, ends_at);
CREATE INDEX IF NOT EXISTS idx_announcements_updated    ON announcements (updated_at DESC);

CREATE TABLE IF NOT EXISTS announcement_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    announcement_id UUID NOT NULL REFERENCES announcements (id) ON DELETE CASCADE,
    label           VARCHAR(300) NOT NULL,
    actor           VARCHAR(200) NOT NULL DEFAULT 'system',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_announcement_events_a ON announcement_events (announcement_id, created_at);
