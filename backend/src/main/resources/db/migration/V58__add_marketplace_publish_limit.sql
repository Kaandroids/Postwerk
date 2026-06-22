-- Plan gating for marketplace publishing. Default true so existing seeded plans can publish.
ALTER TABLE plans
    ADD COLUMN marketplace_publish_enabled boolean NOT NULL DEFAULT true;
