-- Give the STARTER plan a small monthly AI budget instead of disabling AI entirely.
-- cost_limit_cents semantics: -1 = unlimited, 0 = AI disabled, >0 = monthly EUR cent cap.
-- 10 cents = €0.10/month. Only bumps plans that are still at the AI-disabled default (0), so a
-- deliberately-disabled custom plan is left untouched.
UPDATE plans SET cost_limit_cents = 10, updated_at = now()
WHERE name = 'STARTER' AND cost_limit_cents = 0;
