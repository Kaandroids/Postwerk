-- Org suspension for platform staff (admin panel). A suspended org is frozen: its members are
-- blocked from accessing it (OrgContextService rejects the X-Org-Id with 403), while platform
-- staff can still view/manage it through the admin API. suspended_at NULL = active.
-- suspension_reason is recorded for support/audit. Statements are idempotent so a partially
-- applied run can be safely retried.

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ;

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS suspension_reason VARCHAR(500);

COMMENT ON COLUMN organizations.suspended_at IS
    'When the organization was suspended by platform staff; NULL = active. Suspended orgs reject all tenant access.';
