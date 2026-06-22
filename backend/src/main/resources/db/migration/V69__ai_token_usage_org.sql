-- Multi-tenant (#4): the AI cost cap is enforced per-organization. Add organization_id to
-- ai_token_usage so the monthly cost sum is org-scoped, and backfill existing rows from the
-- owner's personal organization.
--
-- Kept NULLABLE: the row is written on the @Async AI-usage path; a missing org must never lose a
-- usage record. New rows carry the billing org threaded from the execution context / chat request.

ALTER TABLE ai_token_usage ADD COLUMN IF NOT EXISTS organization_id UUID;

-- Backfill historical rows from each user's personal organization.
UPDATE ai_token_usage r
   SET organization_id = o.id
  FROM organizations o
 WHERE o.personal
   AND o.owner_user_id = r.user_id
   AND r.organization_id IS NULL;

-- Index the monthly cost-cap query (SUM(cost_micros) WHERE organization_id = ? AND created_at >= ?).
CREATE INDEX IF NOT EXISTS idx_ai_token_usage_org_created
    ON ai_token_usage (organization_id, created_at);
