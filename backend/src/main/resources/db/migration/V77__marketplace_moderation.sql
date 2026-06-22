-- Marketplace Moderation (admin) — staff moderation state, separate from the author-owned lifecycle
-- (PUBLISHED/UNPUBLISHED) and the existing `featured` flag. A taken-down listing is hidden from
-- discovery + detail (buyers' installed copies are preserved); a hidden review is excluded from the
-- public listing detail and from the rating average.

ALTER TABLE marketplace_listings
    ADD COLUMN IF NOT EXISTS taken_down BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE marketplace_reviews
    ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_mk_listings_taken_down
    ON marketplace_listings (taken_down) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_mk_reviews_hidden
    ON marketplace_reviews (hidden);
