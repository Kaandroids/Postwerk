-- Delayed emails queue for DELAY nodes
CREATE TABLE IF NOT EXISTS automation_delayed_emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id UUID NOT NULL REFERENCES emails(id) ON DELETE CASCADE,
    automation_id UUID NOT NULL REFERENCES automations(id) ON DELETE CASCADE,
    node_id UUID NOT NULL REFERENCES automation_nodes(id) ON DELETE CASCADE,
    delayed_until TIMESTAMPTZ NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_delayed_emails_until ON automation_delayed_emails(delayed_until) WHERE NOT processed;

-- Labels column on emails
ALTER TABLE emails ADD COLUMN IF NOT EXISTS labels JSONB DEFAULT '[]';
