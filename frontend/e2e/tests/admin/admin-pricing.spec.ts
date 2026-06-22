import { test, expect } from '../../fixtures/test-fixtures';
import { AdminPricingPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockAdminPricing } from '../../mocks';

test.describe('Admin AI Pricing', () => {
  let pricingPage: AdminPricingPage;

  test.beforeEach(async ({ adminPage }) => {
    pricingPage = new AdminPricingPage(adminPage);
    await adminPage.goto('/dashboard/admin/pricing');
  });

  test('should list model pricing rows', async () => {
    await expect(pricingPage.rows).toHaveCount(3);
    await expect(pricingPage.table).toContainText('gemini-2.5-pro');
    await expect(pricingPage.table).toContainText('1.25');
  });

  test('should open create form', async () => {
    await pricingPage.createBtn.click();
    await expect(pricingPage.form).toBeVisible();
    await expect(pricingPage.modelInput).toBeVisible();
  });

  test('should populate edit form with the model rate', async () => {
    await pricingPage.editButtons.first().click();
    await expect(pricingPage.form).toBeVisible();
    // First row is gemini-2.5-flash (input 0.15). Model input is locked on edit.
    await expect(pricingPage.modelInput).toHaveValue('gemini-2.5-flash');
    await expect(pricingPage.modelInput).toBeDisabled();
    await expect(pricingPage.inputRate).toHaveValue('0.15');
  });

  test('should save a new model rate', async ({ adminPage }) => {
    const api = new MockApi();
    api.post('/api/v1/admin/pricing/models', {
      id: 'mp4', model: 'gemini-2.0-flash', inputPerMillion: 0.10, outputPerMillion: 0.40,
      updatedAt: '2026-06-01T00:00:00Z',
    });
    api.get('/api/v1/admin/pricing/models', [
      ...mockAdminPricing,
      { id: 'mp4', model: 'gemini-2.0-flash', inputPerMillion: 0.10, outputPerMillion: 0.40, updatedAt: '2026-06-01T00:00:00Z' },
    ]);
    await api.apply(adminPage);

    await pricingPage.createBtn.click();
    await pricingPage.modelInput.fill('gemini-2.0-flash');
    await pricingPage.inputRate.fill('0.10');
    await pricingPage.outputRate.fill('0.40');
    await pricingPage.saveBtn.click();
    await expect(pricingPage.rows).toHaveCount(4);
  });

  test('should delete a model rate', async ({ adminPage }) => {
    const api = new MockApi();
    api.delete(/\/api\/v1\/admin\/pricing\/models\//, {}, 204);
    api.get('/api/v1/admin/pricing/models', mockAdminPricing.slice(1));
    await api.apply(adminPage);

    await pricingPage.deleteButtons.first().click();
    await adminPage.locator('.btn-confirm').click();
    await expect(pricingPage.rows).toHaveCount(mockAdminPricing.length - 1);
  });
});
