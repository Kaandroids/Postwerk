import { test, expect } from '../../fixtures/test-fixtures';
import { AutomationsPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockAutomations } from '../../mocks';

test.describe('Automations', () => {
  let automationsPage: AutomationsPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    automationsPage = new AutomationsPage(authenticatedPage);
    const api = new MockApi();
    api.get('/api/v1/automations', mockAutomations);
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations');
  });

  test('should display automation list', async () => {
    await expect(automationsPage.automationCards).toHaveCount(1);
    await expect(automationsPage.cardName(0)).toContainText('Bestellungen verarbeiten');
  });

  test('should show active status badge', async () => {
    await expect(automationsPage.statusBadge(0)).toBeVisible();
  });

  test('should open create form', async () => {
    await automationsPage.addButton.click();
    await expect(automationsPage.nameInput).toBeVisible();
    await expect(automationsPage.descriptionInput).toBeVisible();
  });

  test('should toggle automation status', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .patch(/\/automations\/1\/toggle/, { ...mockAutomations[0], status: 'PAUSED' })
      .get('/api/v1/automations', [{ ...mockAutomations[0], status: 'PAUSED' }]);
    await api.apply(authenticatedPage);

    await automationsPage.toggleButton(0).click();
  });

  test('should show delete button', async () => {
    await expect(automationsPage.deleteButton(0)).toBeVisible();
  });

  test('should display execution stats on card', async () => {
    const values = automationsPage.statValues(0);
    await expect(values).toHaveCount(3);
    await expect(values.nth(0)).toContainText('156');
    await expect(values.nth(1)).toContainText('95%');
    await expect(values.nth(2)).toContainText('4');
  });

  test('should display stat labels', async () => {
    const labels = automationsPage.statLabels(0);
    await expect(labels).toHaveCount(3);
  });

  test('should show last run time in footer', async () => {
    const footer = automationsPage.cardFooter(0);
    await expect(footer).toBeVisible();
    await expect(footer).toContainText('15.06.2024');
  });

  test('should show never-run text when no executions', async ({ authenticatedPage }) => {
    const noRunMock = [{
      ...mockAutomations[0],
      totalExecutions: 0,
      successCount: 0,
      failedCount: 0,
      lastRunAt: null,
    }];
    const api = new MockApi();
    api.get('/api/v1/automations', noRunMock);
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/automations');

    const values = automationsPage.statValues(0);
    await expect(values.nth(0)).toContainText('0');
    await expect(values.nth(1)).toContainText('–');
  });
});
