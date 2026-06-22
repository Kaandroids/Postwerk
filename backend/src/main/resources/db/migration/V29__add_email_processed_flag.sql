ALTER TABLE emails ADD COLUMN processed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_emails_processed ON emails (email_account_id, processed) WHERE processed = FALSE;
