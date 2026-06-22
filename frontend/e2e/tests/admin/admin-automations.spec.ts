import { test, expect } from '../../fixtures/test-fixtures';
import { AdminAutomationsPage } from '../../pages';

test.describe('Admin Automations', () => {
  let autoPage: AdminAutomationsPage;

  test.beforeEach(async ({ adminPage }) => {
    autoPage = new AdminAutomationsPage(adminPage);
    await adminPage.goto('/dashboard/admin/automations');
  });

  test('should display stat cards', async () => {
    await expect(autoPage.statTotal).toBeVisible();
    await expect(autoPage.statSuccess).toBeVisible();
    await expect(autoPage.statFailed).toBeVisible();
    await expect(autoPage.statActive).toBeVisible();
  });

  test('should display success rate bar', async () => {
    await expect(autoPage.successBar).toBeVisible();
    await expect(autoPage.successBar.locator('.rate-pct')).toContainText('94');
  });

  test('should display top automations table', async () => {
    await expect(autoPage.topTable).toBeVisible();
    await expect(autoPage.topTable.locator('tbody tr')).toHaveCount(3);
  });

  test('should display recent executions table', async () => {
    await expect(autoPage.execTable).toBeVisible();
    await expect(autoPage.execTable.locator('tbody tr')).toHaveCount(3);
  });
});
