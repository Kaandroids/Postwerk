-- Create plans table
CREATE TABLE plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,
    token_limit INT NOT NULL DEFAULT 0,
    automation_limit INT NOT NULL DEFAULT 0,
    email_account_limit INT NOT NULL DEFAULT 0,
    price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Add plan_id FK to users table
ALTER TABLE users ADD COLUMN plan_id UUID REFERENCES plans(id);

-- Insert default plans
INSERT INTO plans (name, token_limit, automation_limit, email_account_limit, price) VALUES
    ('FREE', 10000, 3, 2, 0.00),
    ('PRO', 500000, 50, 20, 29.00),
    ('ENTERPRISE', 0, 0, 0, 99.00);
