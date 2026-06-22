import { test, expect } from '@playwright/test';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { dismissCookieConsent } from '../../fixtures/auth.fixture';

/**
 * Password-reset request flow (/auth/reset-password): the user enters their email; on success the
 * page swaps to a confirmation state. Account-recovery entry point.
 */
test.describe('Password reset', () => {
  test.beforeEach(async ({ page }) => {
    await dismissCookieConsent(page);
  });

  test('rejects an invalid email and stays on the form', async ({ page }) => {
    await page.goto('/auth/reset-password');

    await page.locator('#reset-email').fill('not-an-email');
    await page.locator('#reset-email').press('Enter');

    // Client-side validation fails → no success state, form still shown.
    await expect(page.locator('.success-icon')).toHaveCount(0);
    await expect(page.locator('#reset-email')).toBeVisible();
  });

  test('submitting a valid email shows the confirmation state', async ({ page }) => {
    const api = new MockApi();
    api.post('/api/v1/auth/reset-password', { success: true });
    await api.apply(page);
    await page.goto('/auth/reset-password');

    await page.locator('#reset-email').fill('user@example.com');
    await page.locator('#reset-email').press('Enter');

    await expect(page.locator('.success-icon')).toBeVisible();
  });

  test('has a link back to login', async ({ page }) => {
    await page.goto('/auth/reset-password');
    await expect(page.locator('a[routerLink="/auth/login"]')).toBeVisible();
  });
});
