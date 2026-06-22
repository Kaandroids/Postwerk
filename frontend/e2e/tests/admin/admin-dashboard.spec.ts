import { test, expect } from '../../fixtures/test-fixtures';
import { AdminDashboardPage } from '../../pages';

test.describe('Admin Dashboard', () => {
  let dashboardPage: AdminDashboardPage;

  test.beforeEach(async ({ adminPage }) => {
    dashboardPage = new AdminDashboardPage(adminPage);
    await adminPage.goto('/dashboard/admin');
  });

  test('should display KPI cards', async () => {
    await expect(dashboardPage.statUsers).toBeVisible();
    await expect(dashboardPage.statActiveUsers).toBeVisible();
    await expect(dashboardPage.statExecutions).toBeVisible();
    await expect(dashboardPage.statEmails).toBeVisible();
    await expect(dashboardPage.statAiSpend).toBeVisible();
  });

  test('should display AI usage chart and spend-by-model donut', async () => {
    await expect(dashboardPage.chart).toBeVisible();
    await expect(dashboardPage.spendByModel).toBeVisible();
  });

  test('should format large numbers', async () => {
    // totalUsers = 150, rendered via compactNumber → "150"
    await expect(dashboardPage.statUsers.locator('.ad-kpi-value')).toContainText('150');
  });

  test('should expose range selector and refresh control', async () => {
    await expect(dashboardPage.rangeSelector).toBeVisible();
    await expect(dashboardPage.refreshBtn).toBeVisible();
  });

  test('should navigate via sidebar admin nav', async ({ adminPage }) => {
    // Sidebar uses div.dash-nav-item elements, not <a> tags
    await adminPage.locator('.dash-nav-item').getByText('Benutzer').click();
    await expect(adminPage).toHaveURL(/\/admin\/users/);
  });

  test('should render the live System Health + Recent Activity widgets', async ({ adminPage }) => {
    // System Health is now wired to the real subsystems probe (mock: PostgreSQL etc.)
    await expect(adminPage.locator('[data-testid="admin-health-list"]')).toBeVisible();
    await expect(adminPage.locator('[data-testid="admin-health-list"]')).toContainText('PostgreSQL');
    // Recent Activity reads the audit log.
    await expect(adminPage.locator('[data-testid="admin-activity-list"]')).toBeVisible();
    // Quick actions are real navigation shortcuts.
    await adminPage.locator('[data-testid="admin-qa-create-plan"]').click();
    await expect(adminPage).toHaveURL(/\/admin\/plans-subscriptions/);
  });
});
