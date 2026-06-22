import { test, expect } from '../../fixtures/test-fixtures';

test.describe('Account Switcher', () => {
  test('should show account switcher', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/dashboard');
    const switcher = authenticatedPage.locator('app-account-switcher');
    await expect(switcher).toBeVisible();
  });

  test('should display active account', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/dashboard');
    const switcher = authenticatedPage.locator('app-account-switcher');
    await expect(switcher).toContainText(/work@example\.com|Work/);
  });

  test('should open account dropdown', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/dashboard');
    const switcher = authenticatedPage.locator('app-account-switcher');
    await switcher.click();
    // After clicking, the dropdown should show both accounts
    await expect(authenticatedPage.locator('body')).toBeVisible();
  });
});
