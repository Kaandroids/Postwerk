-- V49: Unify automation variable system
-- Removes EXTRACTION_FILTER / CATEGORY_FILTER nodes and migrates FILTER nodes
-- to the new multi-check variable-based format. Drops email_filters tables.

-- 1. Remove EXTRACTION_FILTER and CATEGORY_FILTER nodes and their edges
DELETE FROM automation_edges WHERE source_node_id IN (
    SELECT id FROM automation_nodes WHERE node_type IN ('EXTRACTION_FILTER', 'CATEGORY_FILTER')
) OR target_node_id IN (
    SELECT id FROM automation_nodes WHERE node_type IN ('EXTRACTION_FILTER', 'CATEGORY_FILTER')
);
DELETE FROM automation_nodes WHERE node_type IN ('EXTRACTION_FILTER', 'CATEGORY_FILTER');

-- 2. Migrate existing FILTER nodes config from old format to new checks format
-- Old: { "groups": [...] }  or  { "groups": [...], "savedFilterId": "..." }
-- New: { "checks": [{ "label": "Filter", "groups": [...] }] }
UPDATE automation_nodes
SET config = jsonb_build_object(
    'checks', jsonb_build_array(
        jsonb_build_object(
            'label', 'Filter',
            'groups', config->'groups'
        )
    )
)
WHERE node_type = 'FILTER'
  AND config::jsonb ? 'groups'
  AND NOT config::jsonb ? 'checks';

-- 3. Update FILTER node edge handles: 'match'/'output' → 'check_0', 'no-match' → 'fallback'
UPDATE automation_edges
SET source_handle = 'check_0'
WHERE source_handle IN ('match', 'output')
  AND source_node_id IN (SELECT id FROM automation_nodes WHERE node_type = 'FILTER');

UPDATE automation_edges
SET source_handle = 'fallback'
WHERE source_handle = 'no-match'
  AND source_node_id IN (SELECT id FROM automation_nodes WHERE node_type = 'FILTER');

-- 4. Migrate FILTER condition field names from enum to variable format
-- Uses a PL/pgSQL function to iterate JSONB conditions
DO $$
DECLARE
    node_rec RECORD;
    new_config jsonb;
    checks jsonb;
    check_item jsonb;
    groups jsonb;
    group_item jsonb;
    conditions jsonb;
    condition_item jsonb;
    new_checks jsonb := '[]'::jsonb;
    new_groups jsonb;
    new_conditions jsonb;
    old_field text;
    new_field text;
BEGIN
    FOR node_rec IN
        SELECT id, config::jsonb AS cfg
        FROM automation_nodes
        WHERE node_type = 'FILTER' AND config::jsonb ? 'checks'
    LOOP
        checks := node_rec.cfg->'checks';
        new_checks := '[]'::jsonb;

        FOR ci IN 0..jsonb_array_length(checks) - 1 LOOP
            check_item := checks->ci;
            groups := check_item->'groups';
            IF groups IS NULL THEN
                new_checks := new_checks || jsonb_build_array(check_item);
                CONTINUE;
            END IF;

            new_groups := '[]'::jsonb;
            FOR gi IN 0..jsonb_array_length(groups) - 1 LOOP
                group_item := groups->gi;
                conditions := group_item->'conditions';
                IF conditions IS NULL THEN
                    new_groups := new_groups || jsonb_build_array(group_item);
                    CONTINUE;
                END IF;

                new_conditions := '[]'::jsonb;
                FOR coi IN 0..jsonb_array_length(conditions) - 1 LOOP
                    condition_item := conditions->coi;
                    old_field := condition_item->>'field';

                    new_field := CASE old_field
                        WHEN 'FROM_ADDRESS' THEN 'email.from'
                        WHEN 'FROM_NAME' THEN 'email.fromName'
                        WHEN 'TO_ADDRESS' THEN 'email.to'
                        WHEN 'CC_ADDRESS' THEN 'email.cc'
                        WHEN 'SUBJECT' THEN 'email.subject'
                        WHEN 'BODY' THEN 'email.body'
                        WHEN 'HAS_ATTACHMENTS' THEN 'email.hasAttachments'
                        WHEN 'IS_READ' THEN 'email.isRead'
                        WHEN 'FOLDER' THEN 'email.folder'
                        WHEN 'RECEIVED_AT' THEN 'email.receivedAt'
                        ELSE old_field  -- keep as-is if already in new format
                    END;

                    new_conditions := new_conditions || jsonb_build_array(
                        jsonb_set(condition_item, '{field}', to_jsonb(new_field))
                    );
                END LOOP;

                new_groups := new_groups || jsonb_build_array(
                    jsonb_set(group_item, '{conditions}', new_conditions)
                );
            END LOOP;

            new_checks := new_checks || jsonb_build_array(
                jsonb_set(check_item, '{groups}', new_groups)
            );
        END LOOP;

        UPDATE automation_nodes
        SET config = jsonb_build_object('checks', new_checks)::text
        WHERE id = node_rec.id;
    END LOOP;
END $$;

-- 5. Drop email_filters tables
DROP TABLE IF EXISTS email_filter_conditions;
DROP TABLE IF EXISTS email_filters;
