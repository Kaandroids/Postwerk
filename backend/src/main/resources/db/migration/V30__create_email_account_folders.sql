CREATE TABLE email_account_folders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_account_id UUID NOT NULL REFERENCES email_accounts(id) ON DELETE CASCADE,
    name             TEXT NOT NULL,
    role             TEXT NOT NULL DEFAULT 'OTHER',
    message_count    INTEGER DEFAULT 0,
    unread_count     INTEGER DEFAULT 0,
    last_synced_at   TIMESTAMPTZ,
    UNIQUE (email_account_id, name)
);

CREATE INDEX idx_eaf_account ON email_account_folders(email_account_id);
CREATE INDEX idx_emails_account_folder_uid ON emails(email_account_id, folder, uid);
