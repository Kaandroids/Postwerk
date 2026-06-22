-- Email Health (admin) — per-mailbox sync status so platform staff can monitor IMAP/SMTP
-- connection health across all tenants. Populated by EmailSyncService on each sync attempt.
-- All columns are additive and nullable (paused defaults false) so existing rows are unaffected.

ALTER TABLE email_accounts
    ADD COLUMN IF NOT EXISTS last_sync_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_sync_status  VARCHAR(20),   -- OK | AUTH_ERROR | CONN_ERROR (null = never synced)
    ADD COLUMN IF NOT EXISTS last_error        VARCHAR(500),
    ADD COLUMN IF NOT EXISTS last_error_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS paused            BOOLEAN NOT NULL DEFAULT FALSE;

-- Admin Email Health scans every mailbox cross-tenant; index the status for health filtering.
CREATE INDEX IF NOT EXISTS idx_email_accounts_sync_status
    ON email_accounts (last_sync_status)
    WHERE deleted_at IS NULL;
