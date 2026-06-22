import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockParameterSets, mockEmptyParameterSets } from '../../mocks';

/** Parameter-sets CRUD page: the AI field schemas used by templates/EXTRACT/webhook responses. */
test.describe('Parameter Sets', () => {
  async function applyMocks(page: import('@playwright/test').Page, list: unknown = mockParameterSets) {
    const api = new MockApi();
    api
      .patch(/\/api\/v1\/parameter-sets\/[^/]+\/lock/, { ...mockParameterSets[0], locked: true })
      .get('/api/v1/parameter-sets', list);
    await api.apply(page);
  }

  test('renders the parameter-set list', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/parameter-sets');

    await expect(page.locator('.ps-card')).toHaveCount(2);
    await expect(page.locator('.ps-card').first()).toContainText('Invoice Fields');
  });

  test('opens the create form', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/parameter-sets');

    await page.locator('.ps-add-btn').click();
    await expect(page.locator('#ps-name')).toBeVisible();
  });

  test('toggling the lock calls the lock endpoint', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/parameter-sets');

    const lockReq = page.waitForRequest(
      r => /\/parameter-sets\/ps-1\/lock/.test(r.url()) && r.method() === 'PATCH',
    );
    await page.locator('[data-testid="ps-lock-ps-1"]').click();
    await lockReq;

    await expect(page.locator('[data-testid="ps-lock-ps-1"]')).toHaveClass(/locked/);
  });

  test('empty state when there are no parameter sets', async ({ authenticatedPage: page }) => {
    await applyMocks(page, mockEmptyParameterSets);
    await page.goto('/dashboard/parameter-sets');

    await expect(page.locator('app-empty-state')).toBeVisible();
    await expect(page.locator('.ps-card')).toHaveCount(0);
  });
});
