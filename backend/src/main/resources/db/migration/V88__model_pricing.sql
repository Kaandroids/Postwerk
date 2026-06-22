-- Editable per-model AI pricing (USD per million tokens). Previously these rates lived only in
-- application.yml (gemini.pricing) and required a restart to change; this table makes them
-- admin-editable at runtime. usd_to_eur stays in YAML (a macro FX rate, rarely changed).
-- The yaml values remain as a fallback for any model not present here.
CREATE TABLE model_pricing (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model VARCHAR(100) NOT NULL UNIQUE,
    input_per_million NUMERIC(12, 6) NOT NULL DEFAULT 0,
    output_per_million NUMERIC(12, 6) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed from the current application.yml gemini.pricing.models values.
INSERT INTO model_pricing (model, input_per_million, output_per_million) VALUES
    ('gemini-2.5-flash', 0.15, 0.60),
    ('gemini-2.5-pro', 1.25, 10.00),
    ('gemini-embedding-001', 0.006, 0.0);
