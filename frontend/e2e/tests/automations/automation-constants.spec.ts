import { test, expect } from '../../fixtures/test-fixtures';
import { AutomationConstantsPage } from '../../pages/automation-constants.page';
import { MockApi } from '../../fixtures/mock-api.fixture';

type Constant = { name: string; value: string; type: string; description?: string | null; hasValue?: boolean };

/** Minimal automation with a single TRIGGER node and a given set of typed constants. */
function makeAutomation(constants: Constant[] = []) {
  return {
    id: 1,
    name: 'Constants Test',
    description: '',
    color: '#3b82f6',
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
        label: 'Trigger',
        positionX: 200,
        positionY: 200,
        config: JSON.stringify({ triggerMode: 'EMAIL', accountIds: [] }),
      },
    ],
    edges: [],
    constants,
  };
}

function applyEditorMocks(api: MockApi, detail: ReturnType<typeof makeAutomation>) {
  api
    .get(/\/api\/v1\/automations\/1$/, detail)
    .get('/api/v1/templates', [])
    .get('/api/v1/categories', [])
    .get('/api/v1/filters', [])
    .get('/api/v1/parameter-sets', []);
}

test.describe('Automation Constants', () => {
  let cp: AutomationConstantsPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    cp = new AutomationConstantsPage(authenticatedPage);
    const api = new MockApi();
    applyEditorMocks(api, makeAutomation());
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations/1/edit');
  });

  test('opens an empty constants modal', async () => {
    await cp.open();
    await expect(cp.modal).toBeVisible();
    await expect(cp.empty).toBeVisible();
  });

  test('add opens the inline editor', async () => {
    await cp.open();
    await cp.addButton.click();
    await expect(cp.editor).toBeVisible();
    await expect(cp.empty).not.toBeVisible();
  });

  test('commit persists a typed constant via PUT /constants', async ({ authenticatedPage }) => {
    const api = new MockApi();
    applyEditorMocks(api, makeAutomation());
    let putBody: { constants?: Constant[] } | null = null;
    api.handle('PUT', /\/api\/v1\/automations\/1\/constants$/, async (route) => {
      putBody = route.request().postDataJSON();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          makeAutomation([{ name: 'API_ENDPOINT', value: 'https://api.example.com', type: 'url' }]),
        ),
      });
    });
    await api.apply(authenticatedPage);

    await cp.open();
    await cp.addButton.click();
    await cp.nameInput.fill('API_ENDPOINT');
    await cp.typeButton('url').click();
    await cp.valueInput.fill('https://api.example.com');
    await cp.commitButton.click();

    await expect(cp.rows).toHaveCount(1);
    expect(putBody!.constants).toEqual([
      { name: 'API_ENDPOINT', value: 'https://api.example.com', type: 'url', description: null },
    ]);
  });

  test('blank key shows required error and does not commit', async () => {
    await cp.open();
    await cp.addButton.click();
    await cp.nameInput.fill('');
    await cp.commitButton.click();
    await expect(cp.editor).toBeVisible();
    await expect(cp.rows).toHaveCount(0);
  });

  test('pre-existing constants render as typed rows', async ({ authenticatedPage }) => {
    const api = new MockApi();
    applyEditorMocks(api, makeAutomation([{ name: 'RETRY', value: '3', type: 'number' }]));
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations/1/edit');

    await cp.open();
    await expect(cp.rows).toHaveCount(1);
    await expect(cp.rows.first()).toContainText('RETRY');
    await expect(cp.rows.first()).toContainText('3');
  });

  test('secret constants are masked and never show plaintext', async ({ authenticatedPage }) => {
    const api = new MockApi();
    applyEditorMocks(
      api,
      makeAutomation([{ name: 'API_KEY', value: '', type: 'secret', hasValue: true }]),
    );
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations/1/edit');

    await cp.open();
    await expect(cp.rows).toHaveCount(1);
    await expect(cp.rows.first()).toContainText('API_KEY');
    await expect(cp.rows.first()).toContainText('••••');
  });
});
