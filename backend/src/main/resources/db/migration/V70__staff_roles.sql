-- Platform-staff role tier for the admin panel, layered on top of the coarse USER/ADMIN role.
-- A user is "platform staff" iff staff_role IS NOT NULL. Capabilities are bundled per StaffRole
-- (see StaffRole.java) and emitted as Spring authorities for fine-grained @PreAuthorize checks.
ALTER TABLE users ADD COLUMN staff_role VARCHAR(20);

-- Existing platform admins become SUPER_ADMIN so nothing breaks under the new authorization model.
UPDATE users SET staff_role = 'SUPER_ADMIN' WHERE role = 'ADMIN';

COMMENT ON COLUMN users.staff_role IS
    'Platform staff role (SUPER_ADMIN/ADMIN/SUPPORT/BILLING/MODERATOR/AUDITOR); NULL = regular customer, not staff.';
