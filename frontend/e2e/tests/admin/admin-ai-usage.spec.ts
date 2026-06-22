import { test, expect } from '../../fixtures/test-fixtures';
import { AdminAiUsagePage } from '../../pages';

test.describe('Admin AI Usage', () => {
  let aiPage: AdminAiUsagePage;

  test.beforeEach(async ({ adminPage }) => {
    aiPage = new AdminAiUsagePage(adminPage);
    await adminPage.goto('/dashboard/admin/ai-usage');
  });

  test('should display stat cards with token counts', async () => {
    await expect(aiPage.statTotal).toBeVisible();
    await expect(aiPage.statPrompt).toBeVisible();
    await expect(aiPage.statOutput).toBeVisible();
    await expect(aiPage.statBillable).toBeVisible();
  });

  test('should have period selector', async () => {
    await expect(aiPage.periodSelect).toBeVisible();
  });

  test('should render breakdown bars', async () => {
    await expect(aiPage.modelBars.first()).toBeVisible();
  });

  test('should display cost stat card', async () => {
    await expect(aiPage.statCost).toBeVisible();
    // mockAiUsageStats.totalCostCents = 1245 → €12,45
    await expect(aiPage.statCost).toContainText('12');
  });

  test('should display per-user table', async () => {
    await aiPage.userTable.scrollIntoViewIfNeeded();
    await expect(aiPage.userTable).toBeVisible();
    await expect(aiPage.userTable.locator('tbody tr')).toHaveCount(2);
  });

  test('should display cost column in user table', async () => {
    await aiPage.userTable.scrollIntoViewIfNeeded();
    // mockAiUsageByUser[0].costCents = 845 → €8,45
    const firstRow = aiPage.userTable.locator('tbody tr').first();
    await expect(firstRow).toContainText('8');
  });
});
