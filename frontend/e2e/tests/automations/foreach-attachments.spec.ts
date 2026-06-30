/**
 * Automation Editor — FOREACH iterator over email attachments.
 * Covers the editor surface of the attachment/FOREACH feature: the FOREACH node renders on the
 * canvas, its config panel exposes the source-list + item-alias controls, a downstream FORWARD
 * action offers the current FOREACH item as an attachment source, and the client-side lint flags a
 * FOREACH with no source (FOREACH_NO_SOURCE) while a fully-wired flow stays error-free.
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';

interface NodeSpec { id: string; nodeType: string; label: string; config?: Record<string, unknown>; }

let nextX = 120;
function node(spec: NodeSpec) {
  const x = nextX;
  nextX += 220;
  return {
    id: spec.id,
    nodeType: spec.nodeType,
    label: spec.label,
    positionX: x,
    positionY: 200,
    config: JSON.stringify(spec.config ?? {}),
  };
}

function edge(id: string, src: string, tgt: string, sourceHandle = 'output') {
  return { id, sourceNodeId: src, sourceHandle, targetNodeId: tgt, targetHandle: 'input' };
}

function automation(nodes: ReturnType<typeof node>[], edges: unknown[] = []) {
  nextX = 120;
  return {
    id: 1,
    name: 'FOREACH Test',
    description: '',
    color: '#0891b2',
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

/** TRIGGER(email) → FOREACH(email.attachments) → FORWARD — the canonical per-attachment flow. */
function attachmentFlow(opts: { foreach?: Record<string, unknown>; forward?: Record<string, unknown> } = {}) {
  const nodes = [
    node({ id: 'trigger-1', nodeType: 'TRIGGER', label: 'Posteingang' }),
    node({ id: 'foreach-1', nodeType: 'FOREACH', label: 'Pro Anhang', config: opts.foreach }),
    node({
      id: 'forward-1',
      nodeType: 'EMAIL_ACTION',
      label: 'Weiterleiten',
      config: { actionMode: 'FORWARD', toAddress: 'team@example.com', ...(opts.forward ?? {}) },
    }),
  ];
  const edges = [
    edge('e1', 'trigger-1', 'foreach-1'),
    edge('e2', 'foreach-1', 'forward-1', 'each'),
  ];
  return automation(nodes, edges);
}

test.describe('Automation Editor — FOREACH over attachments', () => {
  test('renders the FOREACH node and exposes its source + alias config', async ({ authenticatedPage: page }) => {
    const api = new MockApi();
    applyEditorMocks(api, attachmentFlow({ foreach: { sourceVariable: 'email.attachments', itemAlias: 'doc' } }));
    await api.apply(page);

    await page.goto('/dashboard/automations/1/edit');

    const foreachNode = page.locator('.ae-node[data-type="FOREACH"]');
    await expect(foreachNode).toBeVisible();

    await foreachNode.dblclick();

    // The source list picker offers the email's attachments, and the configured one is selected.
    const source = page.locator('[data-testid="foreach-source"]');
    await expect(source).toBeVisible();
    await expect(source.locator('option[value="email.attachments"]')).toHaveCount(1);
    await expect(source).toHaveValue('email.attachments');

    // The item alias drives the per-iteration namespace (item.* by default; here renamed to "doc").
    await expect(page.locator('[data-testid="foreach-alias"]')).toHaveValue('doc');
  });

  test('a downstream FORWARD offers the current FOREACH item as an attachment source', async ({ authenticatedPage: page }) => {
    const api = new MockApi();
    applyEditorMocks(api, attachmentFlow({ foreach: { sourceVariable: 'email.attachments', itemAlias: 'doc' } }));
    await api.apply(page);

    await page.goto('/dashboard/automations/1/edit');
    await page.locator('.ae-node[data-type="EMAIL_ACTION"]').dblclick();

    const attachmentSource = page.locator('[data-testid="forward-attachment-source"]');
    await expect(attachmentSource).toBeVisible();
    // Both choices are available: all original attachments, and the current per-item FOREACH element.
    await expect(attachmentSource.locator('option[value="email.attachments"]')).toHaveCount(1);
    await expect(attachmentSource.locator('option[value="doc"]')).toHaveCount(1);
  });

  test('a FOREACH without a source surfaces a blocking lint error', async ({ authenticatedPage: page }) => {
    const api = new MockApi();
    // FOREACH with empty config → FOREACH_NO_SOURCE (error severity).
    applyEditorMocks(api, attachmentFlow({ foreach: {} }));
    await api.apply(page);

    await page.goto('/dashboard/automations/1/edit');

    await expect(page.locator(problemsBtn)).toHaveClass(/has-errors/);

    await page.locator(problemsBtn).click();
    await expect(page.locator('[data-testid="automation-problems-panel"]')).toBeVisible();
    await expect(page.locator(`${problemItem}[data-sev="error"]`).first()).toBeVisible();
  });

  test('a fully-wired FOREACH-over-attachments flow reports no blocking lint errors', async ({ authenticatedPage: page }) => {
    const api = new MockApi();
    applyEditorMocks(api, attachmentFlow({
      foreach: { sourceVariable: 'email.attachments', itemAlias: 'doc' },
      forward: { attachmentSource: 'doc' },
    }));
    await api.apply(page);

    await page.goto('/dashboard/automations/1/edit');

    await expect(page.locator(problemsBtn)).not.toHaveClass(/has-errors/);
  });
});
