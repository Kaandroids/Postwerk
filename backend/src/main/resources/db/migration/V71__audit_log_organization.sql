-- Make the audit trail organization-scoped (multi-tenant #4).
-- Each entry records the organization the action was performed in, so org owners/admins can
-- audit their whole org while members still see only their own actions. Nullable: system jobs
-- and pre-org actions (login/registration) carry no org.
-- Statements are idempotent so a partially-applied run can be safely retried.

ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL;

-- Backfill historical (pre-multi-tenant) entries to each actor's personal organization,
-- which is exactly the single workspace they acted in before orgs existed.
UPDATE audit_log a
SET organization_id = o.id
FROM organizations o
WHERE o.owner_user_id = a.user_id
  AND o.personal = TRUE
  AND a.user_id IS NOT NULL
  AND a.organization_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_audit_log_org_created ON audit_log (organization_id, created_at DESC);
