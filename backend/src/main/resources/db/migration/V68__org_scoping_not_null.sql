-- Multi-tenant (#4 Phase C finale): tighten organization_id to NOT NULL on every table whose
-- create paths now stamp the owning org. Defensively re-backfill any stragglers (rows created
-- between the V67 backfill and the service flips) from the owner's personal organization first.
--
-- LEFT NULLABLE (deferred): ai_token_usage (recorded on the async AI-usage path, no org threaded
-- yet — needed for the org-level AI cost cap) and audit_log (created by AuditService, which stays
-- user-scoped; an org-wide audit view is a later feature).

-- Re-backfill stragglers (no-op when everything is already stamped).
UPDATE email_accounts r           SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE categories r               SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE templates r                SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE parameter_sets r           SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE automations r              SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE secrets r                  SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE webhook_endpoints r        SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE pending_actions r          SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE ai_conversations r         SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE marketplace_acquisitions r SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE marketplace_listings r     SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.author_id AND r.organization_id IS NULL;

-- Tighten to NOT NULL.
ALTER TABLE email_accounts           ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE categories               ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE templates                ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE parameter_sets           ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE automations              ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE secrets                  ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE webhook_endpoints        ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE pending_actions          ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE ai_conversations         ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE marketplace_acquisitions ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE marketplace_listings     ALTER COLUMN organization_id SET NOT NULL;
