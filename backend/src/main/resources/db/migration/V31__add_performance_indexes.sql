-- Composite index for trace existence check (replaces individual email_id + automation_id lookups)
CREATE INDEX IF NOT EXISTS idx_eat_email_automation ON email_automation_traces(email_id, automation_id);

-- Composite index for filtered email listing with folder + sort by received_at
CREATE INDEX IF NOT EXISTS idx_emails_account_folder_received
    ON emails(email_account_id, folder, received_at DESC);

-- Index for unread count queries
CREATE INDEX IF NOT EXISTS idx_emails_account_read
    ON emails(email_account_id, is_read) WHERE is_read = FALSE;

-- GIN index on automations.account_ids for findActiveByAccountId array containment
CREATE INDEX IF NOT EXISTS idx_automations_account_ids
    ON automations USING gin(account_ids);

-- Index for active automation lookup (status + last_run_at for polling)
CREATE INDEX IF NOT EXISTS idx_automations_status_lastrun
    ON automations(status, last_run_at) WHERE deleted_at IS NULL;
