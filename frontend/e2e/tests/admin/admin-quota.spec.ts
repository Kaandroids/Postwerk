import { test, expect } from '../../fixtures/test-fixtures';
import { AdminQuotaPage } from '../../pages';

test.describe('Admin Quota Overrides', () => {
  let quota: AdminQuotaPage;

  test.beforeEach(async ({ adminPage }) => {
    quota = new AdminQuotaPage(adminPage);
    await adminPage.goto('/dashboard/admin/quota');
  });

  test('should render the KPI strip', async () => {
    await expect(quota.kpis).toBeVisible();
    // 3 active overrides from the mock KPIs payload.
    await expect(quota.kpis).toContainText('3');
  });

  test('should render the override table rows', async () => {
    await expect(quota.table).toBeVisible();
    await expect(quota.rows).toHaveCount(3);
    await expect(quota.table).toContainText('Lena Wagner');
    await expect(quota.table).toContainText('Postwerk GmbH');
    await expect(quota.table).toContainText('Aurora Logistik');
  });

  test('should open the create modal from the New override button', async () => {
    await expect(quota.newBtn).toBeEnabled();
    await quota.newBtn.click();
    await expect(quota.modal).toBeVisible();
    await expect(quota.targetPicker).toBeVisible();
    await expect(quota.kindSeg).toBeVisible();
    await expect(quota.reasonInput).toBeVisible();
  });

  test('should open the view modal when a row is clicked', async () => {
    await quota.rows.first().click();
    await expect(quota.modal).toBeVisible();
    // The view modal shows the override id in the subhead.
    await expect(quota.modal).toContainText('qov_');
  });
});
