-- Soft delete columns for GoBD compliance
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN deletion_reason VARCHAR(50);

ALTER TABLE emails ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE email_accounts ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE categories ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE email_filters ADD COLUMN deleted_at TIMESTAMPTZ;

-- Partial indexes for queries excluding soft-deleted records
CREATE INDEX idx_users_not_deleted ON users(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_emails_not_deleted ON emails(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_email_accounts_not_deleted ON email_accounts(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_categories_not_deleted ON categories(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_email_filters_not_deleted ON email_filters(deleted_at) WHERE deleted_at IS NULL;
