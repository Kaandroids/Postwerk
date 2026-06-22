CREATE TABLE ai_conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    title       VARCHAR(200),
    messages    JSONB NOT NULL DEFAULT '[]',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_conversations_user ON ai_conversations (user_id, updated_at DESC);
