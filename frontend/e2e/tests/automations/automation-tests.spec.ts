import { test, expect } from '../../fixtures/test-fixtures';
import { AutomationTestPanelPage } from '../../pages/automation-test-panel.page';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockAutomationDetail } from '../../mocks';
import {
  mockTestCases,
  mockTestResult,
  mockRunAllResponse,
} from '../../mocks/automation-test.mocks';

test.describe('Automation Test Panel', () => {
  let testPanelPage: AutomationTestPanelPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    testPanelPage = new AutomationTestPanelPage(authenticatedPage);

    const api = new MockApi();
    api
      .get(/\/api\/v1\/automations\/1$/, mockAutomationDetail)
      .get(/\/api\/v1\/automations\/1\/tests/, mockTestCases)
      .get('/api/v1/templates', [])
      .get('/api/v1/categories', [])
      .get('/api/v1/filters', [])
      .get('/api/v1/parameter-sets', [])
      .post(/\/api\/v1\/automations\/1\/tests\/1\/run/, mockTestResult)
      .post(/\/api\/v1\/automations\/1\/tests\/run-all/, mockRunAllResponse)
      .post(/\/api\/v1\/automations\/1\/tests$/, mockTestCases[0], 201)
      .delete(/\/api\/v1\/automations\/1\/tests\//, {}, 204);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/automations/1/edit');
    await testPanelPage.openTestPanel();
  });

  test('displays test panel with test cases when opened', async () => {
    await expect(testPanelPage.testPanel).toBeVisible();
    await expect(testPanelPage.testList).toBeVisible();
    await expect(testPanelPage.getTestCards()).toHaveCount(2);
  });

  test('creates a new test case', async ({ authenticatedPage }) => {
    await testPanelPage.clickCreateTest();

    await testPanelPage.fillTestForm({
      name: 'Neue Testfall',
      from: 'sender@example.com',
      to: 'recipient@example.com',
      subject: 'Test Betreff',
      body: 'Test Inhalt der E-Mail',
    });

    await testPanelPage.addAssertion();

    // After save, the list reloads — mock updated response
    const updatedApi = new MockApi();
    updatedApi.get(/\/api\/v1\/automations\/1\/tests/, [...mockTestCases, {
      ...mockTestCases[0],
      id: '3',
      name: 'Neue Testfall',
      lastResult: null,
    }]);
    await updatedApi.apply(authenticatedPage);

    await testPanelPage.clickSave();

    await expect(testPanelPage.testForm).not.toBeVisible();
  });

  test('runs a single test and shows results', async () => {
    await testPanelPage.runButton(0).click();

    // After running, view switches to results
    const resultBadge = testPanelPage.page.locator('[data-testid="test-result-status-badge"]').first();
    await expect(resultBadge).toBeVisible();
    await expect(resultBadge).toContainText('2/2');
  });

  test('runs all tests', async () => {
    await testPanelPage.clickRunAll();

    // After run-all, cards should update with result badges
    await expect(testPanelPage.getTestCards().first().locator('[data-testid="test-result-status-badge"]')).toBeVisible();
  });

  test('deletes a test case', async ({ authenticatedPage }) => {
    // Mock updated list after deletion
    const updatedApi = new MockApi();
    updatedApi.get(/\/api\/v1\/automations\/1\/tests/, [mockTestCases[1]]);
    await updatedApi.apply(authenticatedPage);

    await testPanelPage.deleteButton(0).click();

    await expect(testPanelPage.getTestCards()).toHaveCount(1);
  });

  test('shows error when API fails', async ({ authenticatedPage }) => {
    const errorApi = new MockApi();
    errorApi.post(/\/api\/v1\/automations\/1\/tests\/1\/run/, { message: 'Internal Server Error' }, 500);
    await errorApi.apply(authenticatedPage);

    await testPanelPage.runButton(0).click();

    const errorMessage = authenticatedPage.locator('[data-testid="test-error-message"]');
    await expect(errorMessage).toBeVisible();
  });
});
