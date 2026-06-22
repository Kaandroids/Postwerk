-- Remove enabled column and add optional description
ALTER TABLE email_filters DROP COLUMN IF EXISTS enabled;
ALTER TABLE email_filters ADD COLUMN description VARCHAR(500);
