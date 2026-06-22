-- ═══════════════════════════════════════════════════
-- Denormalized category column on emails (for badge display)
-- ═══════════════════════════════════════════════════
ALTER TABLE emails ADD COLUMN categories JSONB DEFAULT '[]'::jsonb;
CREATE INDEX idx_emails_categories ON emails USING gin (categories);

-- ═══════════════════════════════════════════════════
-- Phase 2: Approval workflow (columns added now, logic later)
-- ═══════════════════════════════════════════════════
ALTER TABLE emails ADD COLUMN approval_status VARCHAR(20);
ALTER TABLE emails ADD COLUMN approval_automation_id UUID;
ALTER TABLE emails ADD COLUMN approval_node_id UUID;
ALTER TABLE emails ADD COLUMN approval_requested_at TIMESTAMPTZ;
ALTER TABLE emails ADD COLUMN approval_resolved_at TIMESTAMPTZ;
ALTER TABLE emails ADD COLUMN approval_action_config JSONB;
CREATE INDEX idx_emails_approval ON emails (approval_status) WHERE approval_status IS NOT NULL;

-- ═══════════════════════════════════════════════════
-- Execution Trace: per email × automation execution record
-- ═══════════════════════════════════════════════════
CREATE TABLE email_automation_traces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id UUID NOT NULL REFERENCES emails(id) ON DELETE CASCADE,
    automation_id UUID NOT NULL REFERENCES automations(id) ON DELETE CASCADE,
    automation_execution_id UUID REFERENCES automation_executions(id) ON DELETE SET NULL,
    automation_name VARCHAR(255) NOT NULL,
    automation_color VARCHAR(20),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    error_message TEXT
);
CREATE INDEX idx_eat_email ON email_automation_traces(email_id);
CREATE INDEX idx_eat_automation ON email_automation_traces(automation_id);

-- ═══════════════════════════════════════════════════
-- Node Trace: per-node execution result
-- ═══════════════════════════════════════════════════
CREATE TABLE email_node_traces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trace_id UUID NOT NULL REFERENCES email_automation_traces(id) ON DELETE CASCADE,
    node_id UUID NOT NULL,
    node_type VARCHAR(50) NOT NULL,
    node_label VARCHAR(255),
    execution_order INT NOT NULL,
    result_status VARCHAR(20) NOT NULL,
    result_detail JSONB,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ent_trace ON email_node_traces(trace_id);
