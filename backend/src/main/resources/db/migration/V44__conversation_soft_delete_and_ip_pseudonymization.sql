-- AI conversation soft-delete (DSGVO Art. 17)
ALTER TABLE ai_conversations ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX idx_ai_conversations_not_deleted ON ai_conversations(deleted_at) WHERE deleted_at IS NULL;

-- IP pseudonymization support: no schema change needed,
-- handled by scheduled service updating ip_address to anonymized form after 90 days.
