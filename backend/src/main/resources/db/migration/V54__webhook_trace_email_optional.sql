-- Inbound webhook automations may run without an email account.
ALTER TABLE email_automation_traces ALTER COLUMN email_id DROP NOT NULL;
