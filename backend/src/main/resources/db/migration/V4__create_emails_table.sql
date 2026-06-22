CREATE TABLE emails (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_account_id UUID NOT NULL REFERENCES email_accounts(id) ON DELETE CASCADE,
    message_id      TEXT,
    folder          TEXT NOT NULL DEFAULT 'INBOX',
    from_address    TEXT,
    from_personal   TEXT,
    to_addresses    TEXT,
    cc_addresses    TEXT,
    subject         TEXT,
    body_text       TEXT,
    body_html       TEXT,
    snippet         TEXT,
    received_at     TIMESTAMPTZ,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    is_starred      BOOLEAN NOT NULL DEFAULT FALSE,
    has_attachments BOOLEAN NOT NULL DEFAULT FALSE,
    size_bytes      BIGINT,
    uid             BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (email_account_id, message_id)
);

CREATE INDEX idx_emails_account_received ON emails (email_account_id, received_at DESC);
CREATE INDEX idx_emails_account_uid ON emails (email_account_id, uid);
