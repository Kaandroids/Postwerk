CREATE TABLE ai_token_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    model VARCHAR(50) NOT NULL,
    operation VARCHAR(30) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    billable_chars INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_token_usage_user_created ON ai_token_usage (user_id, created_at);
