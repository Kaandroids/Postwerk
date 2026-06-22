import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { OrgSwitcherPage } from '../../pages';
import {
  orgListAs,
  orgDetailAs,
  mockOrgsAtFreeLimit,
  mockOrgsBelowFreeLimit,
} from '../../mocks';

/**
 * Role-based access for the redesigned 5-role system. The active org's {@code myRole} drives
 * {@code OrganizationService.can()}, which (a) gates sidebar nav so a role only sees what it can use,
 * and (b) makes the automation editor read-only for non-editors. The backend re-checks every mutation;
 * this is the matching UX layer.
 */

const nav = (key: string) => `[data-nav="${key}"]`;

async function applyRoleMocks(page: import('@playwright/test').Page, role: string) {
  const api = new MockApi();
  api
    .get('/api/v1/organizations/invitations', [])
    .get('/api/v1/organizations/current', orgDetailAs(role))
    .get('/api/v1/organizations', orgListAs(role));
  await api.apply(page);
}

test.describe('Role-based sidebar gating', () => {
  test('VIEWER sees observe-only nav, not admin/write areas', async ({ authenticatedPage: page }) => {
    await applyRoleMocks(page, 'VIEWER');
    await page.goto('/dashboard');

    // Visible: view automations/approvals/audit + the (ungated) organization page.
    await expect(page.locator(nav('automations'))).toBeVisible();
    await expect(page.locator(nav('approvals'))).toBeVisible();
    await expect(page.locator(nav('audit-log'))).toBeVisible();
    await expect(page.locator(nav('organization'))).toBeVisible();

    // Hidden: no write/admin surfaces for a pure observer.
    await expect(page.locator(nav('secrets'))).toHaveCount(0);
    await expect(page.locator(nav('email-accounts'))).toHaveCount(0);
    await expect(page.locator(nav('plans'))).toHaveCount(0);
    await expect(page.locator(nav('settings'))).toHaveCount(0);
    await expect(page.locator(nav('marketplace'))).toHaveCount(0);
    await expect(page.locator(nav('marketplace-library'))).toHaveCount(0);
  });

  test('MEMBER (agent) sees only inbox + approvals + org', async ({ authenticatedPage: page }) => {
    await applyRoleMocks(page, 'MEMBER');
    await page.goto('/dashboard');

    await expect(page.locator(nav('approvals'))).toBeVisible();
    await expect(page.locator(nav('organization'))).toBeVisible();

    // The agent has no automation-building / resource / audit / marketplace surface.
    await expect(page.locator(nav('automations'))).toHaveCount(0);
    await expect(page.locator(nav('templates'))).toHaveCount(0);
    await expect(page.locator(nav('audit-log'))).toHaveCount(0);
    await expect(page.locator(nav('marketplace'))).toHaveCount(0);
    await expect(page.locator(nav('secrets'))).toHaveCount(0);
  });

  test('EDITOR can build + install but not billing/publish/settings', async ({ authenticatedPage: page }) => {
    await applyRoleMocks(page, 'EDITOR');
    await page.goto('/dashboard');

    await expect(page.locator(nav('automations'))).toBeVisible();
    await expect(page.locator(nav('templates'))).toBeVisible();
    await expect(page.locator(nav('marketplace'))).toBeVisible();   // MARKETPLACE_INSTALL
    await expect(page.locator(nav('audit-log'))).toBeVisible();

    // No billing, secrets, publishing, or org settings for the builder.
    await expect(page.locator(nav('plans'))).toHaveCount(0);
    await expect(page.locator(nav('secrets'))).toHaveCount(0);
    await expect(page.locator(nav('marketplace-library'))).toHaveCount(0); // MARKETPLACE_PUBLISH
    await expect(page.locator(nav('settings'))).toHaveCount(0);
  });

  test('OWNER sees every section', async ({ authenticatedPage: page }) => {
    await applyRoleMocks(page, 'OWNER');
    await page.goto('/dashboard');

    for (const key of [
      'automations', 'secrets', 'marketplace', 'marketplace-library',
      'email-accounts', 'plans', 'settings', 'audit-log', 'organization',
    ]) {
      await expect(page.locator(nav(key))).toBeVisible();
    }
  });
});

test.describe('Role-based automation editor (read-only for non-editors)', () => {
  function automationDetail() {
    return {
      id: 1, name: 'RO Test', description: '', color: '#6366f1', kind: 'AUTOMATION', status: 'PAUSED',
      nodeCount: 1, edgeCount: 0, totalExecutions: 0, successCount: 0, failedCount: 0, lastRunAt: null,
      locked: false, createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z',
      nodes: [{ id: 'trigger-1', nodeType: 'TRIGGER', label: 'Posteingang', positionX: 120, positionY: 200, config: '{}' }],
      edges: [], constants: [],
    };
  }

  async function applyEditorMocks(page: import('@playwright/test').Page, role: string) {
    const api = new MockApi();
    api
      .get('/api/v1/organizations/invitations', [])
      .get('/api/v1/organizations/current', orgDetailAs(role))
      .get('/api/v1/organizations', orgListAs(role))
      .get(/\/api\/v1\/automations\/1$/, automationDetail())
      .get('/api/v1/templates', [])
      .get('/api/v1/categories', [])
      .get('/api/v1/filters', [])
      .get('/api/v1/parameter-sets', [])
      .get('/api/v1/secrets', []);
    await api.apply(page);
  }

  test('VIEWER gets a read-only editor: pill shown, palette hidden, node still renders', async ({ authenticatedPage: page }) => {
    await applyEditorMocks(page, 'VIEWER');
    await page.goto('/dashboard/automations/1/edit');

    await expect(page.locator('[data-testid="automation-readonly-pill"]')).toBeVisible();
    await expect(page.locator('.ae-node[data-type="TRIGGER"]')).toBeVisible();
    await expect(page.locator('.ae-toolrail')).toHaveCount(0); // node palette is editor-only
  });

  test('OWNER gets an editable editor: no read-only pill, palette present', async ({ authenticatedPage: page }) => {
    await applyEditorMocks(page, 'OWNER');
    await page.goto('/dashboard/automations/1/edit');

    await expect(page.locator('.ae-node[data-type="TRIGGER"]')).toBeVisible();
    await expect(page.locator('[data-testid="automation-readonly-pill"]')).toHaveCount(0);
    await expect(page.locator('.ae-toolrail')).toBeVisible();
  });
});

test.describe('Free-org ownership cap (org switcher)', () => {
  async function applyMocks(page: import('@playwright/test').Page, orgs: unknown) {
    const api = new MockApi();
    api
      .get('/api/v1/organizations/invitations', [])
      .get('/api/v1/organizations', orgs);
    await api.apply(page);
  }

  test('at the cap (2 owned Starter orgs) → create is replaced by the limit hint', async ({ authenticatedPage: page }) => {
    await applyMocks(page, mockOrgsAtFreeLimit);
    const switcher = new OrgSwitcherPage(page);
    await page.goto('/dashboard');
    await switcher.open();

    await expect(switcher.createLimit).toBeVisible();
    await expect(switcher.createOpen).toHaveCount(0);
  });

  test('below the cap (1 owned Starter org) → create button is offered', async ({ authenticatedPage: page }) => {
    await applyMocks(page, mockOrgsBelowFreeLimit);
    const switcher = new OrgSwitcherPage(page);
    await page.goto('/dashboard');
    await switcher.open();

    await expect(switcher.createOpen).toBeVisible();
    await expect(switcher.createLimit).toHaveCount(0);
  });
});
