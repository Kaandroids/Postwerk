import { test, expect } from '../../fixtures/test-fixtures';
import { WebhookPanelPage } from '../../pages/webhook-panel.page';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockSecrets } from '../../mocks/secret.mocks';

const mockParameterSets = [
  { id: 'ps-1', name: 'Product Response', parameters: [{ name: 'productId' }, { name: 'status' }], createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
];

/** Minimal automation with a single webhook node at a visible position. */
function makeWebhookAutomation(configOverrides: Record<string, unknown> = {}) {
  const baseConfig = {
    url: 'https://hooks.slack.com/services/xxx',
    method: 'POST',
    authType: 'NONE',
    body: '{"text":"{{subject}}"}',
    timeout: 30,
    responseSchemas: [{ name: 'Erfolg', condition: '2xx', parameterSetId: 'ps-1' }],
    ...configOverrides,
  };
  return {
    id: 1,
    name: 'Webhook Test',
    description: '',
    color: '#06b6d4',
    status: 'ACTIVE',
    nodeCount: 1,
    edgeCount: 0,
    totalExecutions: 0,
    successCount: 0,
    failedCount: 0,
    lastRunAt: null,
    locked: false,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    nodes: [
      {
        id: 'webhook-1',
        nodeType: 'WEBHOOK',
        label: 'Slack Webhook',
        positionX: 200,
        positionY: 200,
        config: JSON.stringify(baseConfig),
      },
    ],
    edges: [],
  };
}

function applyEditorMocks(api: MockApi, detail: ReturnType<typeof makeWebhookAutomation>) {
  api
    .get(/\/api\/v1\/automations\/1$/, detail)
    .get('/api/v1/templates', [])
    .get('/api/v1/categories', [])
    .get('/api/v1/filters', [])
    .get('/api/v1/parameter-sets', mockParameterSets)
    .get('/api/v1/secrets', mockSecrets);
}

test.describe('Webhook Panel — Collapsible Sections', () => {
  let wp: WebhookPanelPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    wp = new WebhookPanelPage(authenticatedPage);
    const api = new MockApi();
    applyEditorMocks(api, makeWebhookAutomation());
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations/1/edit');
    await wp.selectWebhookNode();
  });

  test('should display all five collapsible section toggles', async () => {
    await expect(wp.sectionRequest).toBeVisible();
    await expect(wp.sectionAuth).toBeVisible();
    await expect(wp.sectionBody).toBeVisible();
    await expect(wp.sectionResponse).toBeVisible();
    await expect(wp.sectionRetry).toBeVisible();
  });

  test('should have request section open by default with method and URL', async () => {
    await expect(wp.methodSelect).toBeVisible();
    await expect(wp.urlInput).toBeVisible();
  });

  test('should toggle sections open and closed', async () => {
    // Auth section should be closed initially
    await expect(wp.authSelect).not.toBeVisible();

    // Open auth section
    await wp.openSection(wp.sectionAuth);
    await expect(wp.authSelect).toBeVisible();

    // Close auth section
    await wp.openSection(wp.sectionAuth);
    await expect(wp.authSelect).not.toBeVisible();
  });

  test('should show response schema section with name and condition', async () => {
    await wp.openSection(wp.sectionResponse);
    await expect(wp.schemaRows).toHaveCount(1);
    await expect(wp.schemaNameInput(0)).toHaveValue('Erfolg');
    await expect(wp.schemaConditionInput(0)).toHaveValue('2xx');
  });

  test('should show body textarea when method is POST', async () => {
    await wp.openSection(wp.sectionBody);
    await expect(wp.bodyInput).toBeVisible();
  });

  test('response schema section should appear before retry section', async () => {
    const responseBound = await wp.sectionResponse.boundingBox();
    const retryBound = await wp.sectionRetry.boundingBox();
    expect(responseBound).toBeTruthy();
    expect(retryBound).toBeTruthy();
    expect(responseBound!.y).toBeLessThan(retryBound!.y);
  });

  test('should add a new response schema row', async () => {
    await wp.openSection(wp.sectionResponse);
    await expect(wp.schemaRows).toHaveCount(1);
    await wp.addSchemaButton.click();
    await expect(wp.schemaRows).toHaveCount(2);
    await expect(wp.schemaConditionInput(1)).toHaveValue('2xx');
  });
});

test.describe('Webhook Panel — Auth Section', () => {
  let wp: WebhookPanelPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    wp = new WebhookPanelPage(authenticatedPage);
    const api = new MockApi();
    applyEditorMocks(api, makeWebhookAutomation({ authType: 'BEARER', authToken: 'tok_xxx' }));
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations/1/edit');
    await wp.selectWebhookNode();
  });

  test('should show bearer token and secret fields when auth type is BEARER', async () => {
    await wp.openSection(wp.sectionAuth);
    await expect(wp.authTokenInput).toBeVisible();
    await expect(wp.secretSelect).toBeVisible();
  });
});

test.describe('Webhook Panel — Retry Section', () => {
  let wp: WebhookPanelPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    wp = new WebhookPanelPage(authenticatedPage);
    const api = new MockApi();
    applyEditorMocks(api, makeWebhookAutomation({ retryCount: 2, retryDelayMs: 5000, retryOn: ['5xx', '429'] }));
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations/1/edit');
    await wp.selectWebhookNode();
  });

  test('should show retry delay and conditions when retry count > 0', async () => {
    await wp.openSection(wp.sectionRetry);
    await expect(wp.retryCountInput).toBeVisible();
    await expect(wp.retryDelayInput).toBeVisible();
    await expect(wp.retry5xx).toBeChecked();
    await expect(wp.retry429).toBeChecked();
    await expect(wp.retryNetwork).not.toBeChecked();
  });
});
