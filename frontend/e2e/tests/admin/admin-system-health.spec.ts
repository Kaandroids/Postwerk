import { test, expect } from '../../fixtures/test-fixtures';
import { AdminSystemHealthPage } from '../../pages';

test.describe('Admin System Health', () => {
  let sh: AdminSystemHealthPage;

  test.beforeEach(async ({ adminPage }) => {
    sh = new AdminSystemHealthPage(adminPage);
    await adminPage.goto('/dashboard/admin/system-health');
  });

  test('should render the KPI strip', async () => {
    await expect(sh.kpis).toBeVisible();
    await expect(sh.kpis).toContainText('142'); // API latency ms
  });

  test('should render the down-subsystem alert strip', async () => {
    await expect(sh.alert).toBeVisible();
    await expect(sh.alert).toContainText('Redis cache');
  });

  test('should render the subsystem grid', async () => {
    await expect(sh.grid).toBeVisible();
    await expect(sh.cards).toHaveCount(7);
    await expect(sh.grid).toContainText('PostgreSQL');
    await expect(sh.grid).toContainText('AI provider · Gemini');
  });

  test('should open the subsystem detail modal', async ({ adminPage }) => {
    await sh.cards.first().click();
    await expect(sh.modal).toBeVisible();
    // Redis detail mock is served for any subsystem id → shows its last-error callout.
    await expect(sh.modal).toContainText('Redis');
  });

  test('should show the maintenance-mode toggle for a super admin', async () => {
    await expect(sh.maintenance).toBeVisible();
    await expect(sh.maintenance).toBeEnabled();
  });
});
