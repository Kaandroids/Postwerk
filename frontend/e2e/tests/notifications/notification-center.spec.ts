import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockNotificationList, mockEmptyNotificationList } from '../../mocks';

/**
 * Topbar notification center: unread badge, slide-down panel, and mark-all-read. Mounted in the
 * dashboard shell, so it is present on every dashboard route.
 */
test.describe('Notification center', () => {
  const bell = '[data-testid="topbar-notifications-btn"]';
  const badge = '[data-testid="notif-unread-badge"]';
  const panel = '[data-testid="notification-panel"]';
  const item = '[data-testid="notification-item"]';
  const markAll = '[data-testid="notif-mark-all"]';

  async function applyMocks(
    page: import('@playwright/test').Page,
    { count = 2, list = mockNotificationList }: { count?: number; list?: unknown } = {},
  ) {
    const api = new MockApi();
    api
      .post('/api/v1/notifications/read-all', {})
      .patch(/\/api\/v1\/notifications\/[^/]+\/read/, {})
      .get('/api/v1/notifications/unread-count', { count })
      .get('/api/v1/notifications', list); // matches /notifications?unread=...&page=0&size=30
    await api.apply(page);
  }

  test('shows the unread badge from the unread-count endpoint', async ({ authenticatedPage: page }) => {
    await applyMocks(page, { count: 2 });
    await page.goto('/dashboard');

    await expect(page.locator(badge)).toHaveText('2');
  });

  test('opening the bell loads and lists notifications', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard');

    await page.locator(bell).click();
    await expect(page.locator(panel)).toBeVisible();
    await expect(page.locator(item)).toHaveCount(2);
  });

  test('mark-all-read clears the badge and calls the endpoint', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard');
    await page.locator(bell).click();
    await expect(page.locator(panel)).toBeVisible();

    const readAllReq = page.waitForRequest(
      r => /\/notifications\/read-all/.test(r.url()) && r.method() === 'POST',
    );
    await page.locator(markAll).click();
    await readAllReq;

    await expect(page.locator(badge)).toHaveCount(0);
  });

  test('empty inbox shows no badge and an empty panel', async ({ authenticatedPage: page }) => {
    await applyMocks(page, { count: 0, list: mockEmptyNotificationList });
    await page.goto('/dashboard');

    await expect(page.locator(badge)).toHaveCount(0);
    await page.locator(bell).click();
    await expect(page.locator('.nc-empty')).toBeVisible();
    await expect(page.locator(item)).toHaveCount(0);
  });
});
