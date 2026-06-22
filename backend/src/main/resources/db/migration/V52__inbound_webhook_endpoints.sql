-- Inbound webhook receiver endpoints.
-- Each row represents a publicly reachable URL (/api/v1/hooks/{token}) that, when POSTed to,
-- triggers a specific TRIGGER node of an automation. The signing secret is AES-256-GCM encrypted.
CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    automation_id UUID NOT NULL REFERENCES automations(id) ON DELETE CASCADE,
    node_id UUID NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    auth_mode VARCHAR(16) NOT NULL DEFAULT 'NONE',  -- NONE | API_KEY | HMAC
    auth_header_name VARCHAR(64),                    -- for API_KEY, default 'X-API-Key'
    signing_secret TEXT,                             -- AES-256-GCM encrypted, nullable (API_KEY/HMAC)
    parameter_set_id UUID,
    active BOOLEAN NOT NULL DEFAULT true,
    trigger_count BIGINT NOT NULL DEFAULT 0,
    last_triggered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_endpoints_token ON webhook_endpoints(token);
CREATE INDEX idx_webhook_endpoints_user ON webhook_endpoints(user_id);
CREATE INDEX idx_webhook_endpoints_automation ON webhook_endpoints(automation_id);
