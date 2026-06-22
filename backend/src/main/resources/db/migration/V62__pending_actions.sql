-- Supervised mode (#3a): action nodes set to REVIEW park their resolved side effect here for human
-- approval instead of performing it during a live run. action_detail holds the fully resolved
-- payload (rendered subject/body, recipient, folder, url, …) so the user approves exactly what
-- will happen and it can be executed verbatim on approval.
CREATE TABLE pending_actions (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL,
    automation_id UUID NOT NULL,
    email_id      UUID,
    node_id       UUID NOT NULL,
    node_type     VARCHAR(32) NOT NULL,
    node_label    VARCHAR(255),
    action_detail JSONB NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at    TIMESTAMPTZ,
    decided_by    UUID,
    decision_note TEXT
);

-- Approval inbox is queried per user, newest first, usually filtered to PENDING.
CREATE INDEX idx_pending_actions_user_status ON pending_actions (user_id, status, created_at DESC);
CREATE INDEX idx_pending_actions_automation ON pending_actions (automation_id);
