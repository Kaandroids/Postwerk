import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';

/**
 * Dashboard home: the post-login landing page. Shows metric tiles (received count from the active
 * mailbox) when an account exists, or an onboarding CTA when none is connected.
 */
test.describe('Dashboard home', () => {
  test('shows metric tiles with the received count when an account exists', async ({ authenticatedPage: page }) => {
    // authenticatedPage already mocks /email-accounts (non-empty) → an active account exists.
    const api = new MockApi();
    api.get(/\/email-accounts\/[^/]+\/emails/, {
      content: [], totalElements: 42, totalPages: 42, number: 0, size: 1, first: true, last: false,
    });
    await api.apply(page);
    await page.goto('/dashboard');

    await expect(page.locator('.ov-tiles')).toBeVisible();
    await expect(page.locator('.ov-tile')).toHaveCount(4);
    await expect(page.locator('.ov-tile').first().locator('.ov-tile-value')).toContainText('42');
  });

  test('shows the onboarding CTA when no account is connected', async ({ authenticatedPage: page }) => {
    const api = new MockApi();
    api.get('/api/v1/email-accounts', []);
    await api.apply(page);
    await page.goto('/dashboard');

    await expect(page.locator('.onboarding-cta')).toBeVisible();
    await expect(page.locator('.ov-tiles')).toHaveCount(0);
  });

  test('the onboarding CTA navigates to email accounts', async ({ authenticatedPage: page }) => {
    const api = new MockApi();
    api.get('/api/v1/email-accounts', []);
    await api.apply(page);
    await page.goto('/dashboard');

    await page.locator('.onboarding-cta').click();
    await expect(page).toHaveURL(/\/dashboard\/email-accounts/);
  });
});
