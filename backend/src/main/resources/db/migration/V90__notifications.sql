-- V90__notifications.sql
-- User-facing notification system (see doc/NOTIFICATION_SYSTEM_DESIGN.md).
-- `notifications` = the persistent per-recipient inbox (DB is the source of truth; SSE/poll deliver).
-- `notification_preferences` = per-user, per-category channel toggles (absence of a row = category default).

CREATE TABLE notifications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL,                 -- recipient
    organization_id  UUID,                          -- context org (nullable for personal events)
    category         VARCHAR(32)  NOT NULL,         -- AUTOMATION|APPROVAL|QUOTA|MAILBOX|TEAM|MARKETPLACE|SYSTEM
    type             VARCHAR(64)  NOT NULL,         -- APPROVAL_PENDING, AUTOMATION_FAILED, ...
    severity         VARCHAR(16)  NOT NULL,         -- INFO|SUCCESS|WARNING|CRITICAL|ACTION_REQUIRED
    title_key        VARCHAR(120) NOT NULL,         -- i18n key (rendered in the recipient's language)
    body_key         VARCHAR(120),                  -- i18n key (nullable)
    params           JSONB NOT NULL DEFAULT '{}',   -- i18n interpolation params, e.g. {"automationName":"Invoices"}
    link_url         VARCHAR(512),                  -- deep link, e.g. /dashboard/approvals
    payload          JSONB NOT NULL DEFAULT '{}',   -- entity refs for the client, e.g. {"pendingActionId":"..."}
    dedup_key        VARCHAR(200),                  -- (type:entityId) per recipient; suppresses duplicates
    read_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ                    -- optional auto-expiry
);
CREATE INDEX idx_notif_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notif_user_unread  ON notifications(user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX idx_notif_dedup        ON notifications(dedup_key, created_at DESC) WHERE dedup_key IS NOT NULL;

CREATE TABLE notification_preferences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    category        VARCHAR(32) NOT NULL,           -- one row per (user, category)
    in_app_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_notif_pref UNIQUE (user_id, category)
);
CREATE INDEX idx_notif_pref_user ON notification_preferences(user_id);
