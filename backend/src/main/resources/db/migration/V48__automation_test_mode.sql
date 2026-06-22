-- Test mode: allows automations to process emails in dry-run and collect user feedback

CREATE TABLE automation_test_mode_results (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_id     UUID NOT NULL REFERENCES automations(id) ON DELETE CASCADE,
    email_id          UUID NOT NULL REFERENCES emails(id) ON DELETE CASCADE,
    trace_id          UUID REFERENCES email_automation_traces(id) ON DELETE SET NULL,
    simulated_actions JSONB NOT NULL DEFAULT '[]',
    feedback          VARCHAR(20),
    feedback_note     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    feedback_at       TIMESTAMPTZ
);

CREATE INDEX idx_test_mode_results_automation ON automation_test_mode_results(automation_id);
CREATE INDEX idx_test_mode_results_automation_feedback ON automation_test_mode_results(automation_id, feedback);
