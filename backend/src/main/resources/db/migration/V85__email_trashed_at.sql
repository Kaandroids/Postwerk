-- Real Trash (Papierkorb) support.
-- Deleting an email now MOVES it to Trash (trashed_at set) instead of vanishing immediately.
-- A second delete from Trash (or "empty trash") permanently soft-deletes it (deleted_at), which is
-- hidden everywhere by the @SQLRestriction("deleted_at IS NULL") on the Email entity.
-- trashed_at is an overlay distinct from the original `folder` column, so it survives IMAP re-sync
-- (which reconciles folder/uid) and keeps attachment fetch (uid + original folder) working.
ALTER TABLE emails ADD COLUMN trashed_at TIMESTAMPTZ;

-- Partial index for the Papierkorb view (trashed emails per mailbox).
CREATE INDEX idx_emails_trashed ON emails (email_account_id, trashed_at) WHERE trashed_at IS NOT NULL;
