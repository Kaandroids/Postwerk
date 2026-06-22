-- GDPR / Data Requests (DSAR) queue — platform-staff Compliance console.
-- The retention sweep + footprint sources already exist; this adds the request store + timeline.

CREATE TABLE IF NOT EXISTS data_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_user_id  UUID,
    subject_name     VARCHAR(200) NOT NULL,
    subject_email    VARCHAR(320) NOT NULL,
    organization_id  UUID,
    org_name         VARCHAR(200),
    type             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    channel          VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    note             TEXT,
    handler_user_id  UUID,
    handler_name     VARCHAR(200),
    reject_reason    TEXT,
    requested_at     TIMESTAMPTZ  NOT NULL,
    deadline_at      TIMESTAMPTZ  NOT NULL,
    closed_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_data_requests_status        ON data_requests (status);
CREATE INDEX IF NOT EXISTS idx_data_requests_requested     ON data_requests (requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_data_requests_subject_email ON data_requests (subject_email);

CREATE TABLE IF NOT EXISTS data_request_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id  UUID NOT NULL REFERENCES data_requests (id) ON DELETE CASCADE,
    label       VARCHAR(500) NOT NULL,
    actor       VARCHAR(200) NOT NULL DEFAULT 'system',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_data_request_events_req ON data_request_events (request_id, created_at);
