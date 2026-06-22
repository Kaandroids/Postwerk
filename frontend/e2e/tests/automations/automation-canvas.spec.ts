/**
 * Automation Editor — Canvas rendering + Lint safety-net.
 * Deepens editor E2E beyond the config-panel specs: proves the canvas renders a
 * realistic multi-node flow, node selection opens config, and the client-side lint
 * surfaces blocking errors on a broken flow (the activate guard's detection layer).
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';

interface NodeSpec { id: string; nodeType: string; label: string; config?: Record<string, unknown>; }

let nextX = 120;
function node(spec: NodeSpec) {
  const x = nextX;
  nextX += 200;
  return {
    id: spec.id,
    nodeType: spec.nodeType,
    label: spec.label,
    positionX: x,
    positionY: 200,
    config: JSON.stringify(spec.config ?? {}),
  };
}

function automation(nodes: ReturnType<typeof node>[], edges: unknown[] = []) {
  nextX = 120;
  return {
    id: 1,
    name: 'Canvas Test',
    description: '',
    color: '#6366f1',
    kind: 'AUTOMATION',
    status: 'PAUSED',
    nodeCount: nodes.length,
    edgeCount: edges.length,
    totalExecutions: 0,
    successCount: 0,
    failedCount: 0,
    lastRunAt: null,
    locked: false,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    nodes,
    edges,
    constants: [],
  };
}

function edge(id: string, src: string, tgt: string) {
  return { id, sourceNodeId: src, sourceHandle: 'output', targetNodeId: tgt, targetHandle: 'input' };
}

function applyEditorMocks(api: MockApi, detail: unknown) {
  api
    .get(/\/api\/v1\/automations\/1$/, detail)
    .get('/api/v1/templates', [])
    .get('/api/v1/categories', [])
    .get('/api/v1/filters', [])
    .get('/api/v1/parameter-sets', [])
    .get('/api/v1/secrets', []);
}

const problemsBtn = '[data-testid="automation-problems-btn"]';
const problemItem = '[data-testid="automation-problem-item"]';

test.describe('Automation Editor — Canvas & Lint', () => {
  test('renders every node of a multi-type flow on the canvas', async ({ authenticatedPage: page }) => {
    const nodes = [
      node({ id: 'trigger-1', nodeType: 'TRIGGER', label: 'Posteingang' }),
      node({ id: 'filter-1', nodeType: 'FILTER', label: 'Bestellung?', config: { checks: [{ label: 'c', groups: [{ conditions: [{ field: 'email.from', operator: 'CONTAINS', value: 'shop' }] }] }] } }),
      node({ id: 'cat-1', nodeType: 'CATEGORIZE', label: 'Kategorisieren', config: { categoryIds: [1] } }),
      node({ id: 'act-1', nodeType: 'EMAIL_ACTION', label: 'Weiterleiten', config: { actionMode: 'FORWARD', toAddress: 'team@example.com' } }),
      node({ id: 'webhook-1', nodeType: 'WEBHOOK', label: 'Slack', config: { url: 'https://hooks.example.com/x', method: 'POST', authType: 'NONE' } }),
    ];
    const edges = [edge('e1', 'trigger-1', 'filter-1'), edge('e2', 'filter-1', 'cat-1'), edge('e3', 'cat-1', 'act-1'), edge('e4', 'act-1', 'webhook-1')];
    const api = new MockApi();
    applyEditorMocks(api, automation(nodes, edges));
    await api.apply(page);

    await page.goto('/dashboard/automations/1/edit');

    const cards = page.locator('.ae-node');
    await expect(cards).toHaveCount(5);
    for (const t of ['TRIGGER', 'FILTER', 'CATEGORIZE', 'EMAIL_ACTION', 'WEBHOOK']) {
      await expect(page.locator(`.ae-node[data-type="${t}"]`)).toBeVisible();
    }
  });

  test('selecting a node opens its config panel', async ({ authenticatedPage: page }) => {
    const nodes = [
      node({ id: 'trigger-1', nodeType: 'TRIGGER', label: 'Posteingang' }),
      node({ id: 'webhook-1', nodeType: 'WEBHOOK', label: 'Slack', config: { url: 'https://hooks.example.com/x', method: 'POST', authType: 'NONE' } }),
    ];
    const api = new MockApi();
    applyEditorMocks(api, automation(nodes, [edge('e1', 'trigger-1', 'webhook-1')]));
    await api.apply(page);

    await page.goto('/dashboard/automations/1/edit');
    await page.locator('.ae-node[data-type="WEBHOOK"]').dblclick();

    // Webhook config panel renders its URL field
    await expect(page.locator('[data-testid="webhook-url-input"]')).toBeVisible();
  });

  test('a broken flow (no trigger) surfaces a blocking lint error', async ({ authenticatedPage: page }) => {
    // AUTOMATION kind with no TRIGGER node → MISSING_TRIGGER (error severity)
    const nodes = [
      node({ id: 'filter-1', nodeType: 'FILTER', label: 'Filter', config: { checks: [] } }),
      node({ id: 'act-1', nodeType: 'EMAIL_ACTION', label: 'Aktion', config: { actionMode: 'FORWARD' } }),
    ];
    const api = new MockApi();
    applyEditorMocks(api, automation(nodes, [edge('e1', 'filter-1', 'act-1')]));
    await api.apply(page);

    await page.goto('/dashboard/automations/1/edit');

    // Problems button flags errors
    await expect(page.locator(problemsBtn)).toHaveClass(/has-errors/);

    // Opening the panel lists at least one error-severity issue
    await page.locator(problemsBtn).click();
    await expect(page.locator('[data-testid="automation-problems-panel"]')).toBeVisible();
    await expect(page.locator(`${problemItem}[data-sev="error"]`).first()).toBeVisible();
  });

  test('a valid flow reports no blocking lint errors', async ({ authenticatedPage: page }) => {
    const nodes = [
      node({ id: 'trigger-1', nodeType: 'TRIGGER', label: 'Posteingang' }),
      node({ id: 'act-1', nodeType: 'EMAIL_ACTION', label: 'Weiterleiten', config: { actionMode: 'FORWARD', toAddress: 'team@example.com' } }),
    ];
    const api = new MockApi();
    applyEditorMocks(api, automation(nodes, [edge('e1', 'trigger-1', 'act-1')]));
    await api.apply(page);

    await page.goto('/dashboard/automations/1/edit');

    await expect(page.locator(problemsBtn)).not.toHaveClass(/has-errors/);
  });
});
