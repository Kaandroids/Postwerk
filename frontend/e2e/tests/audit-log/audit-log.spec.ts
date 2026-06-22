import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockAuditLogPage, mockEmptyAuditLogPage } from '../../mocks';

/**
 * User-facing audit log: day-grouped event timeline with client-side search/filter and row expansion.
 * Compliance/governance surface — the user's own action trail.
 */
test.describe('Audit log', () => {
  async function applyMocks(
    page: import('@playwright/test').Page,
    data: unknown = mockAuditLogPage,
  ) {
    const api = new MockApi();
    api.get('/api/v1/audit-logs', data);
    await api.apply(page);
  }

  test('renders the event rows', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/audit-log');

    await expect(page.locator('.al-row')).toHaveCount(3);
    await expect(page.locator('.al-timeline')).toBeVisible();
  });

  test('search filters the rows client-side', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/audit-log');
    await expect(page.locator('.al-row')).toHaveCount(3);

    await page.locator('.ib-search-input').fill('Bob');

    await expect(page.locator('.al-row')).toHaveCount(1);
    await expect(page.locator('.al-row')).toContainText('Bob Builder');
  });

  test('expanding a row reveals its detail panel', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/audit-log');

    const firstRow = page.locator('.al-row').first();
    await firstRow.locator('.al-row-head').click();

    await expect(firstRow).toHaveAttribute('data-open', '1');
  });

  test('empty state when there are no audit events', async ({ authenticatedPage: page }) => {
    await applyMocks(page, mockEmptyAuditLogPage);
    await page.goto('/dashboard/audit-log');

    await expect(page.locator('.al-empty')).toBeVisible();
    await expect(page.locator('.al-row')).toHaveCount(0);
  });
});
