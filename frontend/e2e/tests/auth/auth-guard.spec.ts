import { test, expect } from '@playwright/test';
import { test as authTest } from '../../fixtures/test-fixtures';
import { dismissCookieConsent } from '../../fixtures/auth.fixture';

test.describe('Auth Guard', () => {
  test.beforeEach(async ({ page }) => {
    await dismissCookieConsent(page);
  });

  test('should redirect unauthenticated users to login', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/auth\/login/);
  });

  test('should redirect unauthenticated users from nested routes', async ({ page }) => {
    await page.goto('/dashboard/emails');
    await expect(page).toHaveURL(/\/auth\/login/);
  });
});

authTest.describe('Auth Guard (authenticated)', () => {
  authTest('should allow authenticated users to access dashboard', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/dashboard');
    await expect(authenticatedPage).toHaveURL(/\/dashboard/);
  });

  authTest('should allow access to nested dashboard routes', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/dashboard/settings');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/settings/);
  });
});
