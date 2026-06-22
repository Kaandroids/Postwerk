import { test, expect } from '../../fixtures/test-fixtures';
import { AdminPlansPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockAdminPlans } from '../../mocks';

test.describe('Admin Plans', () => {
  let plansPage: AdminPlansPage;

  test.beforeEach(async ({ adminPage }) => {
    plansPage = new AdminPlansPage(adminPage);
    await adminPage.goto('/dashboard/admin/plans');
  });

  test('should display plan cards in list view', async () => {
    await expect(plansPage.planCards).toHaveCount(3);
  });

  test('should open create form', async () => {
    await plansPage.createBtn.click();
    await expect(plansPage.form).toBeVisible();
    await expect(plansPage.nameInput).toBeVisible();
  });

  test('should populate edit form', async () => {
    await plansPage.editButtons.first().click();
    await expect(plansPage.form).toBeVisible();
    await expect(plansPage.nameInput).toHaveValue('FREE');
  });

  test('should save new plan', async ({ adminPage }) => {
    const api = new MockApi();
    api.post('/api/v1/admin/plans', {
      id: 'p4',
      name: 'STARTER',
      tokenLimit: 50000,
      automationLimit: 10,
      emailAccountLimit: 5,
      price: 9.99,
      userCount: 0,
      createdAt: '2026-05-15T00:00:00Z',
    });
    api.get('/api/v1/admin/plans', [
      ...mockAdminPlans,
      { id: 'p4', name: 'STARTER', tokenLimit: 50000, automationLimit: 10, emailAccountLimit: 5, price: 9.99, userCount: 0, createdAt: '2026-05-15T00:00:00Z' },
    ]);
    await api.apply(adminPage);

    await plansPage.createBtn.click();
    await plansPage.nameInput.fill('STARTER');
    await plansPage.saveBtn.click();
  });

  test('should delete plan', async ({ adminPage }) => {
    const api = new MockApi();
    api.delete(/\/api\/v1\/admin\/plans\//, {}, 204);
    api.get('/api/v1/admin/plans', mockAdminPlans.slice(1));
    await api.apply(adminPage);

    // Deletion is now confirmed via the in-app dialog (irreversible action), then the list reloads.
    await plansPage.deleteButtons.first().click();
    await adminPage.locator('.btn-confirm').click();
    await expect(plansPage.planCards).toHaveCount(mockAdminPlans.length - 1);
  });

  test('should display cost limit in plan cards', async () => {
    // mockAdminPlans: FREE=0 (disabled), PRO=500 (€5), ENTERPRISE=-1 (unlimited)
    const freeCard = plansPage.planCards.nth(0);
    const proCard = plansPage.planCards.nth(1);
    const enterpriseCard = plansPage.planCards.nth(2);

    // FREE has costLimitCents=0 → disabled indicator
    await expect(freeCard).toContainText('–');
    // PRO has costLimitCents=500 → €5,00
    await expect(proCard).toContainText('5');
    // ENTERPRISE has costLimitCents=-1 → unlimited
    await expect(enterpriseCard).toContainText('∞');
  });

  test('should show cost limit input in edit form', async () => {
    await plansPage.editButtons.nth(1).click(); // Edit PRO plan
    await expect(plansPage.form).toBeVisible();
    await expect(plansPage.costLimitInput).toBeVisible();
    // PRO costLimitCents=500 → displayed as €5
    await expect(plansPage.costLimitInput).toHaveValue('5');
  });
});
