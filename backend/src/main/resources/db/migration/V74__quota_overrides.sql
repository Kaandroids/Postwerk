-- Per-user / per-org exceptions to the plan's AI cost cap (admin panel "Quota Overrides").
-- Enforcement is ALWAYS organization-scoped: a USER target is resolved to that user's PERSONAL
-- organization, an ORG target enforces on itself. Both the UI target (target_type/target_id) and the
-- resolved enforcement org (organization_id) are stored so the list can show the original target while
-- QuotaService enforces against the org's plan cap.
--
-- kind:
--   CREDIT    -> adds amount_cents of headroom on top of the plan's base cap (sums if multiple)
--   CAP       -> replaces the base cap with amount_cents (most-permissive active CAP wins)
--   UNLIMITED -> removes the cap entirely (any active UNLIMITED override wins over everything)
-- expires_at NULL = no expiry (stays until revoked). An override is "active" when not expired.

CREATE TABLE quota_overrides (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_type         VARCHAR(8)   NOT NULL,                 -- 'USER' | 'ORG'
    target_id           UUID         NOT NULL,                 -- the UI target (user id or org id)
    organization_id     UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    kind                VARCHAR(12)  NOT NULL,                 -- 'CREDIT' | 'CAP' | 'UNLIMITED'
    amount_cents        BIGINT,                                -- required for CREDIT/CAP, null for UNLIMITED
    expires_at          TIMESTAMPTZ,                           -- null = no expiry
    reason              VARCHAR(1000) NOT NULL,                -- mandatory audit reason
    granted_by_user_id  UUID         REFERENCES users(id) ON DELETE SET NULL,
    granted_by_name     VARCHAR(255),                          -- snapshot of the granting staffer
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Enforcement reads all overrides for an org on every AI quota check (effectiveCap).
CREATE INDEX idx_quota_overrides_org ON quota_overrides(organization_id);
-- Expiry filtering for the admin list + the "expiring soon" KPI.
CREATE INDEX idx_quota_overrides_expires ON quota_overrides(expires_at);

COMMENT ON TABLE quota_overrides IS
    'Per-user/per-org exceptions to the plan AI cost cap. Always resolves to an enforcement organization.';
