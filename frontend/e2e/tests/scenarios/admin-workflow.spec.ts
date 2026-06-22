/**
 * Scenario: Admin User Management Workflow
 *
 * Admin navigates admin panel, reviews stats, searches users,
 * opens user detail, and navigates between admin pages.
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { SidebarPage, AdminDashboardPage, AdminUsersPage } from '../../pages';

test.describe('Scenario: Admin Workflow', () => {
  test('admin reviews dashboard stats and manages users', async ({ adminPage }) => {
    await adminPage.goto('/dashboard/admin');
    const adminDash = new AdminDashboardPage(adminPage);

    // ── Step 1: Verify admin dashboard stats are displayed ──
    await expect(adminDash.statUsers).toBeVisible();
    await expect(adminDash.statActiveUsers).toBeVisible();
    await expect(adminDash.statExecutions).toBeVisible();
    await expect(adminDash.statEmails).toBeVisible();

    // ── Step 2: Verify chart is visible ──
    await expect(adminDash.chart).toBeVisible();

    // ── Step 3: Navigate to Users page via sidebar ──
    const sidebar = new SidebarPage(adminPage);
    await sidebar.navigateTo('Benutzer');
    await expect(adminPage).toHaveURL(/\/dashboard\/admin\/users/);

    // ── Step 4: User list is displayed ──
    const users = new AdminUsersPage(adminPage);
    await expect(users.userRows.first()).toBeVisible();

    // ── Step 5: Search for a specific user ──
    await users.searchInput.fill('Max');
    await adminPage.waitForTimeout(400); // debounce

    // ── Step 6: Open user detail drawer ──
    await users.userRows.first().click();
    await expect(users.drawer).toBeVisible();

    // ── Step 7: Close drawer and navigate to AI Usage ──
    await adminPage.keyboard.press('Escape');
    await sidebar.navigateTo('KI-Nutzung');
    await expect(adminPage).toHaveURL(/\/dashboard\/admin\/ai-usage/);

    // ── Step 8: Navigate to Audit Log ──
    await sidebar.navigateTo('Audit-Log');
    await expect(adminPage).toHaveURL(/\/dashboard\/admin\/audit-log/);
  });

  test('non-admin user cannot access admin pages', async ({ authenticatedPage }) => {
    // authenticatedPage has USER role, not ADMIN
    await authenticatedPage.goto('/dashboard/admin');

    // Should be redirected away from admin
    await expect(authenticatedPage).not.toHaveURL(/\/admin/);
  });
});
