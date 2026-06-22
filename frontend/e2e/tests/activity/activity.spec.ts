import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockActivityPage, mockEmptyActivityPage } from '../../mocks';

/**
 * Production activity feed (#3d): what the user's automations did to incoming email, with per-step
 * results and run status. Read-only observability surface.
 */
test.describe('Activity feed', () => {
  async function applyMocks(
    page: import('@playwright/test').Page,
    data: unknown = mockActivityPage,
  ) {
    const api = new MockApi();
    api.get('/api/v1/activity', data);
    await api.apply(page);
  }

  test('renders activity entries with status', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/activity');

    const entries = page.locator('[data-testid="activity-entry"]');
    await expect(entries).toHaveCount(2);
    await expect(entries.first()).toContainText('Invoice Router');
    await expect(entries.first().locator('.av-status')).toHaveText('SUCCESS');
    await expect(entries.nth(1).locator('.av-status')).toHaveText('FAILED');
  });

  test('shows the error message and per-step results of a failed run', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/activity');

    await expect(page.locator('.av-error')).toContainText('SMTP timeout');
    await expect(page.locator('[data-testid="activity-entry"]').nth(1).locator('.av-step')).toHaveCount(1);
  });

  test('empty state when there is no activity yet', async ({ authenticatedPage: page }) => {
    await applyMocks(page, mockEmptyActivityPage);
    await page.goto('/dashboard/activity');

    await expect(page.locator('.av-empty')).toBeVisible();
    await expect(page.locator('[data-testid="activity-entry"]')).toHaveCount(0);
  });
});
