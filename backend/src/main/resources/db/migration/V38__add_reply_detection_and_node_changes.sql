ALTER TABLE emails ADD COLUMN IF NOT EXISTS in_reply_to VARCHAR(500);
CREATE INDEX IF NOT EXISTS idx_emails_in_reply_to ON emails(in_reply_to) WHERE in_reply_to IS NOT NULL;
