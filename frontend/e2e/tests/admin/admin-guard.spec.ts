import { test, expect } from '../../fixtures/test-fixtures';
import { setAuthTokens, dismissCookieConsent } from '../../fixtures/auth.fixture';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockUser } from '../../mocks';

test.describe('Admin Guard', () => {
  test('should redirect non-admin users to /dashboard', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/dashboard/admin');
    await authenticatedPage.waitForURL(/\/dashboard$/);
    await expect(authenticatedPage).toHaveURL(/\/dashboard$/);
  });

  test('should allow admin users to access admin routes', async ({ adminPage }) => {
    await adminPage.goto('/dashboard/admin');
    await expect(adminPage.locator('.dash-title')).toBeVisible();
  });

  test('should redirect unauthenticated users to login', async ({ page }) => {
    await dismissCookieConsent(page);
    await page.goto('/dashboard/admin');
    await page.waitForURL(/\/auth\/login/);
    await expect(page).toHaveURL(/\/auth\/login/);
  });
});
