CREATE TABLE email_filters (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email_account_id  UUID REFERENCES email_accounts(id) ON DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    match_type        VARCHAR(10) NOT NULL DEFAULT 'ALL',
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_filters_user_id ON email_filters(user_id);

CREATE TABLE email_filter_conditions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filter_id   UUID NOT NULL REFERENCES email_filters(id) ON DELETE CASCADE,
    field       VARCHAR(50) NOT NULL,
    operator    VARCHAR(30) NOT NULL,
    value       TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_filter_conditions_filter_id ON email_filter_conditions(filter_id);
