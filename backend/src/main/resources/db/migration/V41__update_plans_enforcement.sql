-- Add api_webhook_enabled column to plans
ALTER TABLE plans ADD COLUMN api_webhook_enabled BOOLEAN NOT NULL DEFAULT false;

-- Rename FREE → STARTER, update limits
UPDATE plans SET name = 'STARTER', token_limit = 0, automation_limit = 3,
  email_account_limit = 2, price = 0.00, api_webhook_enabled = false,
  updated_at = now() WHERE name = 'FREE';

-- Update PRO
UPDATE plans SET token_limit = 100000, automation_limit = 25,
  email_account_limit = 5, price = 19.00, api_webhook_enabled = false,
  updated_at = now() WHERE name = 'PRO';

-- Update ENTERPRISE (-1 = unlimited)
UPDATE plans SET token_limit = -1, automation_limit = -1,
  email_account_limit = -1, price = 99.00, api_webhook_enabled = true,
  updated_at = now() WHERE name = 'ENTERPRISE';

-- Insert BUSINESS plan
INSERT INTO plans (name, token_limit, automation_limit, email_account_limit,
  price, api_webhook_enabled, created_at, updated_at)
VALUES ('BUSINESS', 500000, 100, 20, 49.00, true, now(), now());

-- Assign STARTER to all users without a plan
UPDATE users SET plan_id = (SELECT id FROM plans WHERE name = 'STARTER')
  WHERE plan_id IS NULL AND deleted_at IS NULL;
