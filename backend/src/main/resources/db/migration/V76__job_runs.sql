-- Background Jobs (admin) — per-execution history of the platform's recurring scheduled jobs, so
-- staff can see each job's last run, duration, outcome and a recent-runs timeline. Written best-effort
-- by JobRunService around the real @Scheduled methods (email-sync, automation poller, delayed-email
-- processor, GDPR retention sweep).

CREATE TABLE IF NOT EXISTS job_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          VARCHAR(64)  NOT NULL,     -- registry id, e.g. 'email-sync'
    started_at      TIMESTAMPTZ  NOT NULL,
    finished_at     TIMESTAMPTZ  NOT NULL,
    ok              BOOLEAN      NOT NULL,
    duration_ms     BIGINT       NOT NULL,
    items_processed INTEGER,
    message         VARCHAR(1000),
    triggered_by    VARCHAR(16)  NOT NULL DEFAULT 'schedule',  -- schedule | manual
    actor_user_id   UUID
);

-- The list/detail read the latest runs per job; the timeline reads a job's recent runs newest-first.
CREATE INDEX IF NOT EXISTS idx_job_runs_job_started ON job_runs (job_id, started_at DESC);
