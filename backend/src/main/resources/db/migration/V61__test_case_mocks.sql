-- Per-node mock configuration for a test case. Keyed by node id, each entry declares
-- whether to MOCK (synthesize a response, optionally forcing the failure branch) or run
-- LIVE (perform the real call, bypassing the dry-run short-circuit). NULL/absent = legacy
-- behavior (dry-run simulate). See NodeMock DTO + ExecutionContext mock registry.
ALTER TABLE automation_test_cases
  ADD COLUMN mocks JSONB;
