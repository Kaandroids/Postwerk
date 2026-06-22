-- Unify TRIGGER node: merge SCHEDULE into TRIGGER with triggerMode
-- 1. Convert SCHEDULE nodes to TRIGGER with triggerMode=CRON
UPDATE automation_nodes SET node_type = 'TRIGGER',
  config = jsonb_set(config::jsonb, '{triggerMode}', '"CRON"', true)
WHERE node_type = 'SCHEDULE';

-- 2. Set triggerMode=EMAIL on existing TRIGGER nodes that lack it
UPDATE automation_nodes SET
  config = jsonb_set(config::jsonb, '{triggerMode}', '"EMAIL"', true)
WHERE node_type = 'TRIGGER' AND (config::jsonb ->> 'triggerMode') IS NULL;

-- 3. Rename data_input → input on all edges
UPDATE automation_edges SET target_handle = 'input' WHERE target_handle = 'data_input';
