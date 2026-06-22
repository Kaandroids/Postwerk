-- Friendly, stable per-automation key for node-scoped variable namespaces
-- (http_1, vectorsearch_2, integration_3) instead of the raw node UUID.
ALTER TABLE automation_nodes ADD COLUMN node_key VARCHAR(40);
