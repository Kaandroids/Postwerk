CREATE TABLE automation_test_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id UUID NOT NULL REFERENCES automation_test_cases(id) ON DELETE CASCADE,
    automation_id UUID NOT NULL REFERENCES automations(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    node_results JSONB NOT NULL,
    assertion_results JSONB,
    duration_ms BIGINT,
    error_message TEXT,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_test_results_case ON automation_test_results(test_case_id);
