-- Performance indexes identified in V2 audit (2026-05-18)

-- B5: Speed up automation execution stats queries filtered by status + time
CREATE INDEX IF NOT EXISTS idx_auto_exec_status_triggered
    ON automation_executions (status, triggered_at DESC);

-- B6: Speed up email filtered query subqueries on automation traces
CREATE INDEX IF NOT EXISTS idx_eat_email_status
    ON email_automation_traces (email_id, status);
