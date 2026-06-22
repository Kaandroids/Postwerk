-- Automation Marketplace: listings, acquisitions, reviews, publishable constants.
-- An install creates a hidden, buyer-owned deep copy of the automation (see MarketplaceService).

CREATE TABLE marketplace_listings (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_id     uuid NOT NULL,
    author_id         uuid NOT NULL,
    name              varchar(140) NOT NULL,
    tagline           varchar(280),
    description       text,
    category          varchar(40) NOT NULL,
    visibility        varchar(16) NOT NULL DEFAULT 'PUBLIC',
    pricing_model     varchar(16) NOT NULL DEFAULT 'FREE',
    price             numeric(10,2) NOT NULL DEFAULT 0,
    version           varchar(20) NOT NULL DEFAULT '1.0.0',
    icon              varchar(40),
    color             varchar(40),
    io_in_icon        varchar(40),
    io_in_label       varchar(120),
    io_out_icon       varchar(40),
    io_out_label      varchar(120),
    node_count        int NOT NULL DEFAULT 0,
    constant_count    int NOT NULL DEFAULT 0,
    rating_avg        numeric(3,2) NOT NULL DEFAULT 0,
    rating_count      int NOT NULL DEFAULT 0,
    install_count     int NOT NULL DEFAULT 0,
    featured          boolean NOT NULL DEFAULT false,
    verified          boolean NOT NULL DEFAULT false,
    status            varchar(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz
);

CREATE INDEX idx_mk_listings_category   ON marketplace_listings (category) WHERE deleted_at IS NULL;
CREATE INDEX idx_mk_listings_visibility ON marketplace_listings (visibility) WHERE deleted_at IS NULL;
CREATE INDEX idx_mk_listings_status     ON marketplace_listings (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_mk_listings_author     ON marketplace_listings (author_id) WHERE deleted_at IS NULL;

CREATE TABLE marketplace_acquisitions (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  uuid NOT NULL,
    listing_id               uuid NOT NULL,
    installed_automation_id  uuid NOT NULL,
    pricing_model            varchar(16) NOT NULL DEFAULT 'FREE',
    price                    numeric(10,2) NOT NULL DEFAULT 0,
    status                   varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at               timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_mk_acq_user_listing
    ON marketplace_acquisitions (user_id, listing_id)
    WHERE status <> 'REMOVED';
CREATE INDEX idx_mk_acq_user    ON marketplace_acquisitions (user_id);
CREATE INDEX idx_mk_acq_listing ON marketplace_acquisitions (listing_id);

CREATE TABLE marketplace_reviews (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id   uuid NOT NULL,
    user_id      uuid NOT NULL,
    rating       int NOT NULL,
    text         text,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_mk_review_listing_user ON marketplace_reviews (listing_id, user_id);
CREATE INDEX idx_mk_review_listing ON marketplace_reviews (listing_id);

CREATE TABLE marketplace_publishable_constants (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id   uuid NOT NULL,
    name         varchar(120) NOT NULL,
    description  text,
    sort_order   int NOT NULL DEFAULT 0
);

CREATE INDEX idx_mk_pub_const_listing ON marketplace_publishable_constants (listing_id);
