import { test, expect } from '../../fixtures/test-fixtures';
import { AdminSubscriptionsPage } from '../../pages';

test.describe('Admin Plans & Subscriptions', () => {
  let ps: AdminSubscriptionsPage;

  test.beforeEach(async ({ adminPage }) => {
    ps = new AdminSubscriptionsPage(adminPage);
    await adminPage.goto('/dashboard/admin/plans-subscriptions');
  });

  test('should render the KPI strip with derived MRR', async () => {
    await expect(ps.kpis).toBeVisible();
    await expect(ps.kpis).toContainText('18'); // active subscriptions from mock KPIs
  });

  test('should render the plan catalog', async () => {
    await expect(ps.plans).toBeVisible();
    await expect(ps.plans).toContainText('PRO');
    await expect(ps.plans).toContainText('ENTERPRISE');
  });

  test('should render the subscriptions table rows', async () => {
    await expect(ps.table).toBeVisible();
    await expect(ps.rows).toHaveCount(4);
    await expect(ps.table).toContainText('Postwerk GmbH');
    await expect(ps.table).toContainText('Aurora Logistik');
  });

  test('should open the subscription detail modal on row click', async () => {
    await ps.rows.first().click();
    await expect(ps.modal).toBeVisible();
    await expect(ps.modal).toContainText('Postwerk GmbH');
  });

  test('should open the plan editor from the New plan button', async () => {
    await expect(ps.newPlanBtn).toBeEnabled();
    await ps.newPlanBtn.click();
    await expect(ps.editor).toBeVisible();
  });
});
