-- Role model #4 refinement: introduce the EDITOR (builder) role between MEMBER and ADMIN.
--
-- Previously MEMBER meant "builds and runs automations". The model now splits that into:
--   * EDITOR  — builds/edits/tests automations & resources, installs from marketplace (cannot go live)
--   * MEMBER  — operates the running system (works granted inboxes, decides the approval queue)
-- so existing members were effectively editors. Promote them to EDITOR to preserve their capabilities.
--
-- `memberships.role` is a VARCHAR(20) with no CHECK constraint, so the new value needs no DDL.
UPDATE memberships SET role = 'EDITOR', updated_at = now() WHERE role = 'MEMBER';
