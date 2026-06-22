-- Hidden automations are buyer-owned copies installed from a PRIVATE marketplace listing.
-- Their nodes/edges/constant values must never be returned via the normal automation API.
ALTER TABLE automations
    ADD COLUMN hidden boolean NOT NULL DEFAULT false;
