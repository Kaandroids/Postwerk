-- Unify REPLY, FORWARD, MOVE_FOLDER into EMAIL_ACTION node type with actionMode discriminator

-- Convert REPLY → EMAIL_ACTION with actionMode=REPLY
UPDATE automation_nodes SET node_type = 'EMAIL_ACTION',
  config = jsonb_set(config::jsonb, '{actionMode}', '"REPLY"', true)
WHERE node_type = 'REPLY';

-- Convert FORWARD → EMAIL_ACTION with actionMode=FORWARD
UPDATE automation_nodes SET node_type = 'EMAIL_ACTION',
  config = jsonb_set(config::jsonb, '{actionMode}', '"FORWARD"', true)
WHERE node_type = 'FORWARD';

-- Convert MOVE_FOLDER → EMAIL_ACTION with actionMode=MOVE_FOLDER
UPDATE automation_nodes SET node_type = 'EMAIL_ACTION',
  config = jsonb_set(config::jsonb, '{actionMode}', '"MOVE_FOLDER"', true)
WHERE node_type = 'MOVE_FOLDER';
