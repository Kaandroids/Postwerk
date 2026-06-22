import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockAnalyticsOverview, mockAnalyticsOverviewEmpty, mockAnalyticsDetail } from '../../mocks';

/**
 * Analytics overview + per-automation detail: KPI rendering, range switching (refetch), drill-down
 * navigation, and the zero-runs empty state.
 */
test.describe('Analytics overview', () => {
  async function applyMocks(
    page: import('@playwright/test').Page,
    overview: unknown = mockAnalyticsOverview,
  ) {
    const api = new MockApi();
    api
      .get(/\/api\/v1\/analytics\/automations\//, mockAnalyticsDetail)
      .get('/api/v1/analytics/overview', overview);
    await api.apply(page);
  }

  test('renders KPI tiles and the top-automations table', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/analytics');

    await expect(page.locator('[data-testid="analytics-kpi-runs"]')).toBeVisible();
    await expect(page.locator('[data-testid="analytics-kpi-success"]')).toContainText('94%');
    await expect(page.locator('[data-testid="analytics-automation-row"]')).toHaveCount(2);
  });

  test('switching the range refetches the overview', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/analytics');
    await expect(page.locator('[data-testid="analytics-kpi-runs"]')).toBeVisible();

    const rangeReq = page.waitForRequest(
      r => /\/analytics\/overview\?range=7d/.test(r.url()) && r.method() === 'GET',
    );
    await page.locator('[data-testid="analytics-range-7d"]').click();
    await rangeReq;
  });

  test('clicking an automation row opens its detail', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/analytics');

    await page.locator('[data-testid="analytics-automation-row"]').first().click();

    await expect(page).toHaveURL(/\/dashboard\/analytics\/auto-1/);
    await expect(page.locator('[data-testid="analytics-detail-back"]')).toBeVisible();
  });

  test('empty state when there are no runs in the range', async ({ authenticatedPage: page }) => {
    await applyMocks(page, mockAnalyticsOverviewEmpty);
    await page.goto('/dashboard/analytics');

    await expect(page.locator('app-empty-state')).toBeVisible();
    await expect(page.locator('[data-testid="analytics-kpi-runs"]')).toHaveCount(0);
  });
});

test.describe('Analytics detail', () => {
  async function applyMocks(page: import('@playwright/test').Page) {
    const api = new MockApi();
    api
      .get(/\/api\/v1\/analytics\/automations\//, mockAnalyticsDetail)
      .get('/api/v1/analytics/overview', mockAnalyticsOverview);
    await api.apply(page);
  }

  test('loads the automation detail with trend and recent runs', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/analytics/auto-1');

    await expect(page.locator('[data-testid="analytics-detail-trend"]')).toBeVisible();
    await expect(page.locator('[data-testid="analytics-detail-run-row"]')).toHaveCount(2);
  });

  test('back button returns to the overview', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/analytics/auto-1');
    await expect(page.locator('[data-testid="analytics-detail-back"]')).toBeVisible();

    await page.locator('[data-testid="analytics-detail-back"]').click();

    await expect(page).toHaveURL(/\/dashboard\/analytics$/);
  });
});
