ALTER TABLE email_filters ADD COLUMN color VARCHAR(7) NOT NULL DEFAULT '#3b82f6';
ALTER TABLE email_filters DROP COLUMN match_type;
ALTER TABLE email_filter_conditions ADD COLUMN group_index INTEGER NOT NULL DEFAULT 0;
