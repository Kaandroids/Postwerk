-- Multi-tenant foundation (#4 Phase A): Organization is the tenant that owns all resources.
-- A user joins one or more organizations via memberships (Slack-style switcher); per-mailbox
-- access is granted via mailbox_grants. Plan/quota attaches to the organization.

CREATE TABLE organizations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    slug          VARCHAR(140),
    plan_id       UUID REFERENCES plans(id),
    owner_user_id UUID,                                   -- creator / single user of a personal org
    personal      BOOLEAN NOT NULL DEFAULT FALSE,         -- auto-created single-user workspace
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_organizations_plan  ON organizations (plan_id);
CREATE INDEX idx_organizations_owner ON organizations (owner_user_id) WHERE personal = TRUE;

-- A user's role within an organization. Unique per (organization, user).
CREATE TABLE memberships (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id    UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role               VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    invited_by_user_id UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_membership_org_user ON memberships (organization_id, user_id);
CREATE INDEX idx_membership_user ON memberships (user_id);

-- Per-mailbox access (Member/Viewer); Owner/Admin bypass with implicit all-mailbox access.
CREATE TABLE mailbox_grants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id UUID NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    mailbox_id    UUID NOT NULL REFERENCES email_accounts(id) ON DELETE CASCADE,
    can_read      BOOLEAN NOT NULL DEFAULT TRUE,
    can_send      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_mailbox_grant ON mailbox_grants (membership_id, mailbox_id);
CREATE INDEX idx_mailbox_grant_mailbox ON mailbox_grants (mailbox_id);
