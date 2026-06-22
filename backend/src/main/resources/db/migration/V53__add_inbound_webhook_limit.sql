-- Plan quota: maximum number of active inbound webhook trigger endpoints per user.
-- -1 = unlimited, 0 = disabled.
ALTER TABLE plans ADD COLUMN inbound_webhook_limit INT NOT NULL DEFAULT 0;

UPDATE plans SET inbound_webhook_limit = 0  WHERE name = 'STARTER';
UPDATE plans SET inbound_webhook_limit = 3  WHERE name = 'PRO';
UPDATE plans SET inbound_webhook_limit = 10 WHERE name = 'BUSINESS';
UPDATE plans SET inbound_webhook_limit = -1 WHERE name = 'ENTERPRISE';
