import { test, expect } from '../../fixtures/test-fixtures';
import { SidebarPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockEmailPage, mockCategories, mockTemplates, mockAutomations } from '../../mocks';

test.describe('Dashboard Navigation', () => {
  test.beforeEach(async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get(/\/email-accounts\/\d+\/emails/, mockEmailPage)
      .get('/api/v1/categories', mockCategories)
      .get('/api/v1/templates', mockTemplates)
      .get('/api/v1/automations', mockAutomations);
    await api.apply(authenticatedPage);
  });

  test('should navigate to emails page', async ({ authenticatedPage }) => {
    const sidebar = new SidebarPage(authenticatedPage);
    await authenticatedPage.goto('/dashboard');
    // "Posteingang" is inside the collapsible "Email" group
    await sidebar.expandGroup('Email');
    await sidebar.navItem('Posteingang').click();
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/emails/);
  });

  test('should navigate to categories page', async ({ authenticatedPage }) => {
    const sidebar = new SidebarPage(authenticatedPage);
    await authenticatedPage.goto('/dashboard');
    await sidebar.navigateTo('Kategorien');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/categories/);
  });

  test('should navigate to templates page', async ({ authenticatedPage }) => {
    const sidebar = new SidebarPage(authenticatedPage);
    await authenticatedPage.goto('/dashboard');
    await sidebar.navigateTo('Vorlagen');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/templates/);
  });

  test('should navigate to settings page', async ({ authenticatedPage }) => {
    const sidebar = new SidebarPage(authenticatedPage);
    await authenticatedPage.goto('/dashboard');
    await sidebar.navigateTo('Einstellungen');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/settings/);
  });

  test('should show user name in sidebar', async ({ authenticatedPage }) => {
    const sidebar = new SidebarPage(authenticatedPage);
    await authenticatedPage.goto('/dashboard');
    await expect(sidebar.userName).toContainText('Max Mustermann');
  });
});
