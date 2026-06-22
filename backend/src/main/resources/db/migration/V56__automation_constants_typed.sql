-- Upgrade per-automation constants from a flat {"NAME":"value"} map to a typed
-- object form {"NAME":{"value":"...","type":"text"}}. Backfills existing rows
-- whose values are still plain strings; rows already in object form are untouched.
UPDATE automations
SET constants = (
    SELECT COALESCE(
        jsonb_object_agg(key, jsonb_build_object('value', value, 'type', 'text')),
        '{}'::jsonb
    )
    FROM jsonb_each_text(constants)
)
WHERE constants IS NOT NULL
  AND constants <> '{}'::jsonb
  AND EXISTS (
      SELECT 1 FROM jsonb_each(constants) e WHERE jsonb_typeof(e.value) <> 'object'
  );
