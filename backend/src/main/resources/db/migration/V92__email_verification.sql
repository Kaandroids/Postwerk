-- Email verification state for users.
--
-- New sign-ups start UNVERIFIED and must confirm their address (via a link mailed to them)
-- before they can log in. Every account that already exists at migration time is grandfathered
-- as verified so the gate never locks out current users.

ALTER TABLE users
    ADD COLUMN email_verified    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN email_verified_at TIMESTAMPTZ;

UPDATE users
   SET email_verified    = TRUE,
       email_verified_at = COALESCE(created_at, now())
 WHERE email_verified = FALSE;
