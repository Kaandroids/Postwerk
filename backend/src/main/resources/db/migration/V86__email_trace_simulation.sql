-- Mark automation traces produced by a TESTING (Simulationsmodus) run so the UI can badge them.
ALTER TABLE email_automation_traces ADD COLUMN simulation BOOLEAN NOT NULL DEFAULT FALSE;

-- Finalize traces orphaned in RUNNING by the pre-fix test-mode bug (dry-run node traces were attached
-- to the cascade=ALL parent collection without a trace_id, blowing up the test-mode transaction at
-- commit so the trace never reached a terminal status). Such traces can never complete on their own.
UPDATE email_automation_traces
SET status = 'FAILED',
    completed_at = NOW(),
    error_message = 'Auto-finalized: stuck in RUNNING (legacy test-mode persistence bug)'
WHERE status = 'RUNNING' AND started_at < NOW() - INTERVAL '10 minutes';
