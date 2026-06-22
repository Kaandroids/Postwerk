import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockUsageResponse, mockUsageResponseStarter } from '../../mocks/plan.mocks';

test.describe('Topbar AI Limiter Widget', () => {
  test('should display limiter for PRO plan with usage percentage', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/dashboard');
    await authenticatedPage.waitForTimeout(500);

    const limiter = authenticatedPage.locator('[data-testid="topbar-ai-limiter"]');
    await expect(limiter).toBeVisible({ timeout: 10000 });

    // costUsedCents=124, costLimitCents=500 → 25%
    await expect(limiter).toContainText('AI Limit');
    await expect(limiter).toContainText('25%');
  });

  test('should not display limiter for STARTER plan (costLimitCents=0)', async ({ page }) => {
    const { dismissCookieConsent, setAuthTokens } = await import('../../fixtures/auth.fixture');
    await dismissCookieConsent(page);
    await setAuthTokens(page);

    const api = new MockApi();
    api
      .get('/api/v1/users/me/usage', mockUsageResponseStarter)
      .get('/api/v1/users/me', { id: 1, email: 'test@example.com', fullName: 'Test', planName: 'Starter' })
      .get('/api/v1/email-accounts', [])
      .get(/\/api\/v1\/email-accounts\/\d+\/folders/, []);
    await api.apply(page);

    await page.goto('/dashboard');
    await page.waitForTimeout(500);
    const limiter = page.locator('[data-testid="topbar-ai-limiter"]');
    await expect(limiter).not.toBeVisible();
  });

  test('should show unlimited symbol for enterprise plan', async ({ page }) => {
    const { dismissCookieConsent, setAuthTokens } = await import('../../fixtures/auth.fixture');
    await dismissCookieConsent(page);
    await setAuthTokens(page);

    const unlimitedUsage = {
      plan: { name: 'ENTERPRISE', tokenLimit: 0, automationLimit: 0, emailAccountLimit: 0, apiWebhookEnabled: true, costLimitCents: -1 },
      usage: { tokensUsedThisMonth: 50000, activeAutomations: 10, emailAccounts: 5, costUsedCents: 3200 },
      billingPeriod: { start: '2026-05-01T00:00:00Z', end: '2026-06-01T00:00:00Z' },
    };

    const api = new MockApi();
    api
      .get('/api/v1/users/me/usage', unlimitedUsage)
      .get('/api/v1/users/me', { id: 1, email: 'test@example.com', fullName: 'Test', planName: 'Enterprise' })
      .get('/api/v1/email-accounts', [])
      .get(/\/api\/v1\/email-accounts\/\d+\/folders/, []);
    await api.apply(page);

    await page.goto('/dashboard');
    await page.waitForTimeout(500);
    const limiter = page.locator('[data-testid="topbar-ai-limiter"]');
    await expect(limiter).toBeVisible({ timeout: 10000 });
    await expect(limiter).toContainText('∞');
  });

  test('should navigate to plans page on click', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/dashboard');
    await authenticatedPage.waitForTimeout(500);
    const limiter = authenticatedPage.locator('[data-testid="topbar-ai-limiter"]');
    await expect(limiter).toBeVisible({ timeout: 10000 });
    await limiter.click();
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/plans/);
  });
});
