-- Supervised mode (#3a, part 2): snapshot of the resolved variable context at park time, so an
-- approved action re-executes the node with the exact same {{email.*}} / {{extraction_N.*}} /
-- {{category.*}} values that produced the previewed payload (WYSIWYG, no drift).
ALTER TABLE pending_actions
    ADD COLUMN context_snapshot JSONB;
