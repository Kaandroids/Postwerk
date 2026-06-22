-- Multi-tenant (#4 Phase A): add a nullable organization_id to every owned resource table.
-- Columns are populated by the V67 backfill. They stay NULLABLE through Phases A/B (nothing reads
-- them yet); Phase C wires all create paths + flips queries to org scoping, then sets NOT NULL.
-- FK allows NULL, so adding the reference now is safe for existing rows.

ALTER TABLE email_accounts           ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE categories               ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE templates                ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE automations              ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE parameter_sets           ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE secrets                  ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE webhook_endpoints        ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE pending_actions          ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE ai_conversations         ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE ai_token_usage           ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE marketplace_acquisitions ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE marketplace_listings     ADD COLUMN organization_id UUID REFERENCES organizations(id);
ALTER TABLE audit_log                ADD COLUMN organization_id UUID REFERENCES organizations(id);

CREATE INDEX idx_email_accounts_org           ON email_accounts (organization_id);
CREATE INDEX idx_categories_org               ON categories (organization_id);
CREATE INDEX idx_templates_org                ON templates (organization_id);
CREATE INDEX idx_automations_org              ON automations (organization_id);
CREATE INDEX idx_parameter_sets_org           ON parameter_sets (organization_id);
CREATE INDEX idx_secrets_org                  ON secrets (organization_id);
CREATE INDEX idx_webhook_endpoints_org        ON webhook_endpoints (organization_id);
CREATE INDEX idx_pending_actions_org          ON pending_actions (organization_id);
CREATE INDEX idx_ai_conversations_org         ON ai_conversations (organization_id);
CREATE INDEX idx_ai_token_usage_org           ON ai_token_usage (organization_id, created_at);
CREATE INDEX idx_marketplace_acquisitions_org ON marketplace_acquisitions (organization_id);
CREATE INDEX idx_marketplace_listings_org     ON marketplace_listings (organization_id);
CREATE INDEX idx_audit_log_org                ON audit_log (organization_id);
