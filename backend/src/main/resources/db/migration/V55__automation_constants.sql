-- Per-automation user-defined constants (plain key/value pairs).
-- Resolved at execution time as {{const.NAME}} placeholders.
ALTER TABLE automations
    ADD COLUMN constants JSONB NOT NULL DEFAULT '{}'::jsonb;
