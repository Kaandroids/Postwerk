/**
 * Scenario: Automation Management Workflow
 *
 * User navigates to automations, views stats, toggles automation status,
 * and manages automation lifecycle.
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { SidebarPage, AutomationsPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import {
  mockAutomations,
  mockCategories,
  mockTemplates,
} from '../../mocks';

test.describe('Scenario: Automation Workflow', () => {
  test('user views automations and checks stats', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.get('/api/v1/automations', mockAutomations);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/automations');
    const automations = new AutomationsPage(authenticatedPage);

    // ── Step 1: Verify automation card is visible ──
    await expect(automations.automationCards).toHaveCount(1);
    await expect(automations.cardName(0)).toContainText('Bestellungen verarbeiten');

    // ── Step 2: Check status badge shows ACTIVE ──
    await expect(automations.statusBadge(0)).toContainText('Aktiv');

    // ── Step 3: Verify execution stats ──
    const statValues = automations.statValues(0);
    await expect(statValues.nth(0)).toContainText('156');
  });

  test('user navigates between multiple feature pages', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get('/api/v1/automations', mockAutomations)
      .get('/api/v1/categories', mockCategories)
      .get('/api/v1/templates', mockTemplates);
    await api.apply(authenticatedPage);

    const sidebar = new SidebarPage(authenticatedPage);
    await authenticatedPage.goto('/dashboard');

    // ── Step 1: Navigate to automations ──
    await sidebar.navigateTo('Automationen');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/automations/);
    await expect(authenticatedPage.locator('.auto-card')).toHaveCount(1);

    // ── Step 2: Navigate to categories ──
    await sidebar.navigateTo('Kategorien');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/categories/);

    // ── Step 3: Navigate to templates ──
    await sidebar.navigateTo('Vorlagen');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/templates/);

    // ── Step 4: Back to automations ──
    await sidebar.navigateTo('Automationen');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/automations/);
  });

  test('user deletes an automation after confirmation', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get('/api/v1/automations', mockAutomations)
      .delete(/\/automations\/\d+/, {});
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/automations');
    const automations = new AutomationsPage(authenticatedPage);

    // ── Step 1: Verify card exists ──
    await expect(automations.automationCards).toHaveCount(1);

    // ── Step 2: Click delete ──
    await automations.deleteButton(0).click();

    // ── Step 3: Confirm deletion ──
    const reloadApi = new MockApi();
    reloadApi.get('/api/v1/automations', []);
    await reloadApi.apply(authenticatedPage);

    await authenticatedPage.locator('.btn-confirm').click();

    // ── Step 4: Empty state should appear ──
    await expect(automations.emptyState).toBeVisible();
  });
});
