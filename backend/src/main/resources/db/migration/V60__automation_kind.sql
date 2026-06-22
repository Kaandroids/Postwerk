ALTER TABLE automations
  ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'AUTOMATION';
-- existing rows default to AUTOMATION; integrations are created with kind='INTEGRATION'
CREATE INDEX idx_automations_user_kind ON automations (user_id, kind);

-- Marketplace listings carry the kind of the automation they publish so discover/detail
-- surfaces can label and filter "Integration" vs "Automation" listings.
ALTER TABLE marketplace_listings
  ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'AUTOMATION';
