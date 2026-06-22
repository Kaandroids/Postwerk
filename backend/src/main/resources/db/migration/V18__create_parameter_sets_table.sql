CREATE TABLE parameter_sets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    description      TEXT NOT NULL,
    positive_example TEXT,
    negative_example TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at       TIMESTAMPTZ
);

CREATE INDEX idx_parameter_sets_user_id ON parameter_sets(user_id);
CREATE INDEX idx_parameter_sets_not_deleted ON parameter_sets(deleted_at) WHERE deleted_at IS NULL;
