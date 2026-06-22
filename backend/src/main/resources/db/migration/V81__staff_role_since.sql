-- Staff & Roles roster: track when a user's current staff role was first granted.
-- Backfilled to updated_at for existing staff so the roster's "staff since" isn't blank.

ALTER TABLE users ADD COLUMN IF NOT EXISTS staff_role_since TIMESTAMPTZ;

UPDATE users SET staff_role_since = COALESCE(updated_at, created_at)
WHERE staff_role IS NOT NULL AND staff_role_since IS NULL;
