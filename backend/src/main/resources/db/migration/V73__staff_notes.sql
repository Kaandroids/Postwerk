-- Internal staff-only notes about a customer user (admin panel "Users support tooling").
-- Never shown to the customer. author_name/author_email are SNAPSHOTTED at write time so the note
-- survives the author's account deletion (author_user_id then nulls out via ON DELETE SET NULL).
CREATE TABLE staff_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    author_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    author_name VARCHAR(255),
    author_email VARCHAR(255),
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Notes are read newest-first per target user; this index covers the only access path.
CREATE INDEX idx_staff_notes_target ON staff_notes(target_user_id, created_at DESC);
