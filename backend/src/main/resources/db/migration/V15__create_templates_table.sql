CREATE TABLE templates (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    subject    TEXT NOT NULL,
    body       TEXT NOT NULL,
    params     JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_templates_user_id ON templates(user_id);
CREATE INDEX idx_templates_not_deleted ON templates(deleted_at) WHERE deleted_at IS NULL;
