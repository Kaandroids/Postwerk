-- Add cost tracking to ai_token_usage
ALTER TABLE ai_token_usage ADD COLUMN cost_micros INTEGER NOT NULL DEFAULT 0;

-- Add cost limit to plans
ALTER TABLE plans ADD COLUMN cost_limit_cents INTEGER NOT NULL DEFAULT 0;

-- Set cost limits for existing plans
UPDATE plans SET cost_limit_cents = 0    WHERE name = 'STARTER';
UPDATE plans SET cost_limit_cents = 500  WHERE name = 'PRO';
UPDATE plans SET cost_limit_cents = 2000 WHERE name = 'BUSINESS';
UPDATE plans SET cost_limit_cents = -1   WHERE name = 'ENTERPRISE';
