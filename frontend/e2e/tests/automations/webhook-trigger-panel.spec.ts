import { test, expect } from '../../fixtures/test-fixtures';
import { WebhookTriggerPanelPage } from '../../pages/webhook-trigger-panel.page';
import { MockApi } from '../../fixtures/mock-api.fixture';
import {
  mockWebhookEndpoint,
  mockWebhookEndpointRegenerated,
  mockWebhookEndpointApiKey,
  mockWebhookEndpointHmac,
  mockGeneratedSecret,
} from '../../mocks/webhook-endpoint.mocks';

const mockParameterSets = [
  { id: 'ps-1', name: 'Order Payload', parameters: [{ name: 'orderId' }], createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
];

/** Automation with a single INBOUND webhook TRIGGER node. */
function makeWebhookTriggerAutomation(configOverrides: Record<string, unknown> = {}) {
  const baseConfig = {
    triggerMode: 'WEBHOOK',
    accountIds: [],
    webhookEndpointId: 'wep-1',
    webhookToken: 'tok_abc123',
    parameterSetId: 'ps-1',
    ...configOverrides,
  };
  return {
    id: 1,
    name: 'Inbound Webhook Test',
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
        id: 'trigger-1',
        nodeType: 'TRIGGER',
        label: 'Inbound Hook',
        positionX: 200,
        positionY: 200,
        config: JSON.stringify(baseConfig),
      },
    ],
    edges: [],
  };
}

function applyEditorMocks(api: MockApi, detail: ReturnType<typeof makeWebhookTriggerAutomation>, endpoint = mockWebhookEndpoint) {
  api
    .get(/\/api\/v1\/automations\/1$/, detail)
    .get('/api/v1/templates', [])
    .get('/api/v1/categories', [])
    .get('/api/v1/filters', [])
    .get('/api/v1/parameter-sets', mockParameterSets)
    .get(/\/api\/v1\/webhook-endpoints\/[^/]+$/, endpoint);
}

test.describe('Inbound Webhook Trigger Panel', () => {
  let wp: WebhookTriggerPanelPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    wp = new WebhookTriggerPanelPage(authenticatedPage);
    const api = new MockApi();
    applyEditorMocks(api, makeWebhookTriggerAutomation());
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations/1/edit');
    await wp.selectTriggerNode();
  });

  test('should display the generated inbound URL', async () => {
    await expect(wp.urlInput).toBeVisible();
    await expect(wp.urlInput).toHaveValue(mockWebhookEndpoint.url);
  });

  test('should show copy and regenerate buttons', async () => {
    await expect(wp.copyButton).toBeVisible();
    await expect(wp.regenerateButton).toBeVisible();
  });

  test('copy button writes the URL to the clipboard', async ({ authenticatedPage }) => {
    await authenticatedPage.evaluate(() => {
      (window as unknown as { __copied?: string }).__copied = undefined;
      navigator.clipboard.writeText = (text: string) => {
        (window as unknown as { __copied?: string }).__copied = text;
        return Promise.resolve();
      };
    });
    await wp.copyButton.click();
    const copied = await authenticatedPage.evaluate(() => (window as unknown as { __copied?: string }).__copied);
    expect(copied).toBe(mockWebhookEndpoint.url);
  });

  test('regenerate replaces the URL with a new token', async ({ authenticatedPage }) => {
    const api = new MockApi();
    applyEditorMocks(api, makeWebhookTriggerAutomation());
    api.post(/\/api\/v1\/webhook-endpoints\/[^/]+\/regenerate-token$/, mockWebhookEndpointRegenerated);
    await api.apply(authenticatedPage);

    await wp.regenerateButton.click();
    await expect(wp.urlInput).toHaveValue(mockWebhookEndpointRegenerated.url);
  });

  test('should show the parameter-set mapping select with options', async () => {
    await expect(wp.parameterSetSelect).toBeVisible();
    await expect(wp.parameterSetSelect.locator('option[value="ps-1"]')).toHaveCount(1);
  });

  test('auth mode select defaults to the endpoint auth mode', async () => {
    await expect(wp.authModeSelect).toBeVisible();
    await expect(wp.authModeSelect).toHaveValue('NONE');
  });
});

test.describe('Inbound Webhook Trigger Panel — API_KEY auth', () => {
  let wp: WebhookTriggerPanelPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    wp = new WebhookTriggerPanelPage(authenticatedPage);
    const api = new MockApi();
    applyEditorMocks(api, makeWebhookTriggerAutomation(), mockWebhookEndpointApiKey);
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations/1/edit');
    await wp.selectTriggerNode();
  });

  test('shows header-name input and secret controls for API_KEY', async () => {
    await expect(wp.authModeSelect).toHaveValue('API_KEY');
    await expect(wp.headerNameInput).toBeVisible();
    await expect(wp.headerNameInput).toHaveValue('X-API-Key');
    await expect(wp.generateSecretButton).toBeVisible();
  });
});

test.describe('Inbound Webhook Trigger Panel — HMAC auth', () => {
  let wp: WebhookTriggerPanelPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    wp = new WebhookTriggerPanelPage(authenticatedPage);
    const api = new MockApi();
    applyEditorMocks(api, makeWebhookTriggerAutomation(), mockWebhookEndpointHmac);
    api.post(/\/api\/v1\/webhook-endpoints\/[^/]+\/generate-secret$/, mockGeneratedSecret);
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations/1/edit');
    await wp.selectTriggerNode();
  });

  test('shows "secret set" state and no header-name input for HMAC', async () => {
    await expect(wp.authModeSelect).toHaveValue('HMAC');
    await expect(wp.headerNameInput).not.toBeVisible();
    await expect(wp.secretState).toBeVisible();
  });

  test('generate secret reveals the freshly generated value once', async () => {
    await wp.generateSecretButton.click();
    await expect(wp.secretValue).toBeVisible();
    await expect(wp.secretValue).toHaveValue(mockGeneratedSecret.secret);
  });
});

test.describe('Inbound Webhook Trigger Panel — unsaved node', () => {
  test('shows save-to-generate hint when no token exists yet', async ({ authenticatedPage }) => {
    const wp = new WebhookTriggerPanelPage(authenticatedPage);
    const api = new MockApi();
    // Node without webhookToken/endpointId => not yet saved
    applyEditorMocks(api, makeWebhookTriggerAutomation({ webhookEndpointId: undefined, webhookToken: undefined }));
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations/1/edit');
    await wp.selectTriggerNode();

    await expect(wp.saveHint).toBeVisible();
    await expect(wp.urlInput).not.toBeVisible();
  });
});
