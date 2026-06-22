import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';

/**
 * Integrations = trigger-less reusable automations (kind INTEGRATION), listed/created on their own
 * page and edited in the normal automation editor.
 */
const mockIntegrations = [
  { id: 'int-1', name: 'Slack Notifier', description: 'Posts to Slack', type: 'EMAIL', kind: 'INTEGRATION', status: 'PAUSED', color: '#8b5cf6', nodeCount: 3, edgeCount: 2, totalExecutions: 0, successCount: 0, failedCount: 0, locked: false, lastRunAt: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', testModeStats: null },
  { id: 'int-2', name: 'CRM Sync', description: 'Pushes leads to the CRM', type: 'EMAIL', kind: 'INTEGRATION', status: 'ACTIVE', color: '#10b981', nodeCount: 5, edgeCount: 4, totalExecutions: 0, successCount: 0, failedCount: 0, locked: false, lastRunAt: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', testModeStats: null },
];

test.describe('Integrations', () => {
  async function applyMocks(page: import('@playwright/test').Page, list: unknown = mockIntegrations) {
    const api = new MockApi();
    api.get('/api/v1/automations/integrations', list);
    await api.apply(page);
  }

  test('renders the integrations list', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/integrations');

    await expect(page.locator('.auto-card')).toHaveCount(2);
    await expect(page.locator('[data-testid="intg-card-int-1"]')).toContainText('Slack Notifier');
  });

  test('opens the create form', async ({ authenticatedPage: page }) => {
    await applyMocks(page);
    await page.goto('/dashboard/integrations');

    await page.locator('[data-testid="intg-add-btn"]').click();
    await expect(page.locator('#intg-name')).toBeVisible();
  });

  test('creates an integration (kind INTEGRATION)', async ({ authenticatedPage: page }) => {
    const created = { ...mockIntegrations[0], id: 'int-new', name: 'New Integration', nodes: [], edges: [], constants: [] };
    const api = new MockApi();
    api
      .post('/api/v1/automations', created)
      .get(/\/api\/v1\/automations\/int-new$/, created)
      .get('/api/v1/templates', []).get('/api/v1/categories', []).get('/api/v1/filters', [])
      .get('/api/v1/parameter-sets', []).get('/api/v1/secrets', [])
      .get('/api/v1/automations/integrations', mockIntegrations);
    await api.apply(page);
    await page.goto('/dashboard/integrations');

    await page.locator('[data-testid="intg-add-btn"]').click();
    await page.locator('#intg-name').fill('New Integration');

    const createReq = page.waitForRequest(r => r.url().endsWith('/api/v1/automations') && r.method() === 'POST');
    await page.locator('[data-testid="intg-save-btn"]').click();
    const req = await createReq;

    expect(JSON.parse(req.postData() || '{}').kind).toBe('INTEGRATION');
  });

  test('empty state when there are no integrations', async ({ authenticatedPage: page }) => {
    await applyMocks(page, []);
    await page.goto('/dashboard/integrations');

    await expect(page.locator('app-empty-state')).toBeVisible();
    await expect(page.locator('.auto-card')).toHaveCount(0);
  });
});
