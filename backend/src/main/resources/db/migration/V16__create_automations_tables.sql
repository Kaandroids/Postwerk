CREATE TABLE automations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    type                VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    status              VARCHAR(20) NOT NULL DEFAULT 'PAUSED',
    account_ids         UUID[] NOT NULL DEFAULT '{}',
    polling_interval_ms BIGINT NOT NULL DEFAULT 300000,
    color               VARCHAR(7) NOT NULL DEFAULT '#3b82f6',
    flow_data           JSONB NOT NULL DEFAULT '{}',
    last_run_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE automation_nodes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_id UUID NOT NULL REFERENCES automations(id) ON DELETE CASCADE,
    node_type     VARCHAR(50) NOT NULL,
    label         VARCHAR(255),
    position_x    DOUBLE PRECISION NOT NULL DEFAULT 0,
    position_y    DOUBLE PRECISION NOT NULL DEFAULT 0,
    config        JSONB NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE automation_edges (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_id  UUID NOT NULL REFERENCES automations(id) ON DELETE CASCADE,
    source_node_id UUID NOT NULL REFERENCES automation_nodes(id) ON DELETE CASCADE,
    source_handle  VARCHAR(50) NOT NULL DEFAULT 'output',
    target_node_id UUID NOT NULL REFERENCES automation_nodes(id) ON DELETE CASCADE,
    target_handle  VARCHAR(50) NOT NULL DEFAULT 'input',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE automation_executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_id   UUID NOT NULL REFERENCES automations(id) ON DELETE CASCADE,
    triggered_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    processed_count INTEGER NOT NULL DEFAULT 0,
    error_log       TEXT
);

CREATE INDEX idx_automations_user ON automations(user_id);
CREATE INDEX idx_automations_not_deleted ON automations(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_auto_nodes_auto ON automation_nodes(automation_id);
CREATE INDEX idx_auto_edges_auto ON automation_edges(automation_id);
CREATE INDEX idx_auto_exec_auto ON automation_executions(automation_id);
