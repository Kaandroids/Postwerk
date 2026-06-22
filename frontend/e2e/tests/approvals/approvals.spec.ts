import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockPendingActionPage, mockEmptyPendingActionPage, mockCategories } from '../../mocks';

/**
 * Supervised-mode approval inbox (#3a): the Agent/Admin reviews parked side effects and
 * approves / rejects / reclassifies them. Each decision removes the card from the list.
 */
test.describe('Approvals (supervised-mode inbox)', () => {
  async function applyMocks(
    page: import('@playwright/test').Page,
    pageData: { content: unknown[] } = mockPendingActionPage,
  ) {
    const api = new MockApi();
    api
      .post(/\/pending-actions\/[^/]+\/approve/, {})
      .post(/\/pending-actions\/[^/]+\/reject/, {})
      .post(/\/pending-actions\/[^/]+\/reclassify/, {})
      .get('/api/v1/pending-actions/count', { pending: pageData.content.length })
      .get('/api/v1/pending-actions', pageData)
      .get('/api/v1/categories', mockCategories);
    await api.apply(page);
  }

  test('renders the pending-action list', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/approvals');

    await expect(page.locator('[data-testid="pending-action"]')).toHaveCount(2);
    await expect(page.locator('[data-testid="pending-action"]').first()).toContainText('EMAIL_ACTION');
  });

  test('approving an action removes it from the list', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/approvals');
    const cards = page.locator('[data-testid="pending-action"]');
    await expect(cards).toHaveCount(2);

    const approveReq = page.waitForRequest(
      r => /\/pending-actions\/[^/]+\/approve/.test(r.url()) && r.method() === 'POST',
    );
    await cards.first().locator('[data-testid="approve-btn"]').click();
    await approveReq;

    await expect(cards).toHaveCount(1);
  });

  test('rejecting an action removes it from the list', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/approvals');
    const cards = page.locator('[data-testid="pending-action"]');
    await expect(cards).toHaveCount(2);

    const rejectReq = page.waitForRequest(
      r => /\/pending-actions\/[^/]+\/reject/.test(r.url()) && r.method() === 'POST',
    );
    await cards.first().locator('[data-testid="reject-btn"]').click();
    await rejectReq;

    await expect(cards).toHaveCount(1);
  });

  test('correcting a wrong category reclassifies and removes the action', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/approvals');
    const cards = page.locator('[data-testid="pending-action"]');
    // Only the first card carries a triggerCategory → the "wrong category" affordance.
    const firstCard = cards.first();
    await firstCard.locator('[data-testid="wrong-category"]').click();

    const reclassifyReq = page.waitForRequest(
      r => /\/pending-actions\/[^/]+\/reclassify\?categoryId=/.test(r.url()) && r.method() === 'POST',
    );
    await firstCard.locator('[data-testid="correct-category-select"]').selectOption({ index: 1 });
    await reclassifyReq;

    await expect(cards).toHaveCount(1);
  });

  test('empty state when there are no pending actions', async ({ authenticatedPage: page }) => {
    await applyMocks(page, mockEmptyPendingActionPage);
    await page.goto('/dashboard/approvals');

    await expect(page.locator('app-empty-state')).toBeVisible();
    await expect(page.locator('[data-testid="pending-action"]')).toHaveCount(0);
  });
});
