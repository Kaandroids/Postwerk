CREATE TABLE IF NOT EXISTS email_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    color VARCHAR(7) NOT NULL DEFAULT '#f59e0b',
    read_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    write_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    imap_host VARCHAR(255),
    imap_port INTEGER,
    imap_username VARCHAR(255),
    imap_password TEXT,
    imap_ssl BOOLEAN,
    smtp_host VARCHAR(255),
    smtp_port INTEGER,
    smtp_username VARCHAR(255),
    smtp_password TEXT,
    smtp_ssl BOOLEAN,
    sync_from_date DATE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, email)
);

CREATE INDEX IF NOT EXISTS idx_email_accounts_user_id ON email_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_email_accounts_user_default ON email_accounts(user_id, is_default) WHERE is_default = TRUE;
