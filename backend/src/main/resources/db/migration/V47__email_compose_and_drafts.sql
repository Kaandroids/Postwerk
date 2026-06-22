-- V47: Add BCC support and draft attachments table for email compose

ALTER TABLE emails ADD COLUMN bcc_addresses TEXT;

CREATE TABLE draft_attachments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id   UUID NOT NULL REFERENCES emails(id) ON DELETE CASCADE,
    file_name  VARCHAR(255) NOT NULL,
    content_type VARCHAR(127) NOT NULL,
    size_bytes BIGINT NOT NULL,
    data       BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_draft_attachments_email_id ON draft_attachments(email_id);
