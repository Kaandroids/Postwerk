-- Multi-tenant (#4 Phase A): personal-org backfill. Every existing user gets an auto-created
-- personal Organization (they become OWNER), all their resources are reassigned to it, and they
-- are granted full read/send on all their mailboxes. Single-user accounts become 1-member orgs and
-- notice nothing. owner_user_id is the join key; it stays as a convenience pointer afterwards.
-- Includes soft-deleted users so their (still-present) resource rows also get an org for the
-- eventual NOT NULL constraint in Phase C.

-- 1. One personal organization per user, inheriting the user's plan.
INSERT INTO organizations (id, name, plan_id, owner_user_id, personal, created_at, updated_at)
SELECT gen_random_uuid(),
       COALESCE(NULLIF(btrim(u.full_name), ''), u.email),
       u.plan_id,
       u.id,
       TRUE,
       now(), now()
FROM users u;

-- 2. OWNER membership for each user in their personal org.
INSERT INTO memberships (id, organization_id, user_id, role, status, created_at, updated_at)
SELECT gen_random_uuid(), o.id, o.owner_user_id, 'OWNER', 'ACTIVE', now(), now()
FROM organizations o
WHERE o.personal = TRUE;

-- 3. Backfill organization_id on every owned resource (by owner user).
UPDATE email_accounts r           SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE categories r               SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE templates r                SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE automations r              SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE parameter_sets r           SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE secrets r                  SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE webhook_endpoints r        SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE pending_actions r          SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE ai_conversations r         SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE ai_token_usage r           SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE marketplace_acquisitions r SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;
UPDATE marketplace_listings r     SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.author_id AND r.organization_id IS NULL;
UPDATE audit_log r                SET organization_id = o.id FROM organizations o WHERE o.personal AND o.owner_user_id = r.user_id   AND r.organization_id IS NULL;

-- 4. Grant the owner full read/send on every mailbox they own.
INSERT INTO mailbox_grants (id, membership_id, mailbox_id, can_read, can_send, created_at)
SELECT gen_random_uuid(), m.id, ea.id, TRUE, TRUE, now()
FROM memberships m
JOIN organizations o ON o.id = m.organization_id AND o.personal = TRUE
JOIN email_accounts ea ON ea.user_id = m.user_id AND ea.deleted_at IS NULL;
