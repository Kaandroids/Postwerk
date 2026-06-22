ALTER TABLE users
    ADD COLUMN privacy_accepted_at TIMESTAMPTZ,
    ADD COLUMN terms_accepted_at   TIMESTAMPTZ,
    ADD COLUMN privacy_version     VARCHAR(20),
    ADD COLUMN marketing_opted_in_at TIMESTAMPTZ;

-- Backfill existing users with their created_at as consent timestamp
UPDATE users
SET privacy_accepted_at  = created_at,
    terms_accepted_at    = created_at,
    privacy_version      = '2026-05',
    marketing_opted_in_at = CASE WHEN marketing_opt_in THEN created_at ELSE NULL END;
