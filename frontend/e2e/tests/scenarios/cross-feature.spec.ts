/**
 * Scenario: Cross-Feature Integration
 *
 * Tests that span multiple features in a single user session:
 * multi-page navigation, folder management while browsing,
 * and AI chat overlay on different pages.
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { SidebarPage, EmailsPage, CategoriesPage, SettingsPage, AiChatPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import {
  mockEmailPage,
  mockEmailDetail,
  mockCategories,
  mockTemplates,
  mockAutomations,
  mockFoldersWithCustom,
  mockFolderCreated,
} from '../../mocks';

test.describe('Scenario: Cross-Feature Integration', () => {
  test('user navigates through all major sections in one session', async ({
    authenticatedPage,
  }) => {
    const api = new MockApi();
    api
      .get(/\/emails\/\d+/, mockEmailDetail)
      .get(/\/emails/, mockEmailPage)
      .get('/api/v1/categories', mockCategories)
      .get('/api/v1/templates', mockTemplates)
      .get('/api/v1/automations', mockAutomations);
    await api.apply(authenticatedPage);

    const sidebar = new SidebarPage(authenticatedPage);

    // ── Step 1: Dashboard home ──
    await authenticatedPage.goto('/dashboard');
    await expect(sidebar.userName).toContainText('Max Mustermann');

    // ── Step 2: Emails via direct navigation ──
    await authenticatedPage.goto('/dashboard/emails');
    const emails = new EmailsPage(authenticatedPage);
    await expect(emails.emailCards).toHaveCount(10);

    // ── Step 3: Read an email ──
    await emails.emailCard(0).click();
    await expect(emails.expandedBody()).toBeVisible();

    // ── Step 4: Categories ──
    await sidebar.navigateTo('Kategorien');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/categories/);
    const categories = new CategoriesPage(authenticatedPage);
    await expect(categories.categoryCards).toHaveCount(2);

    // ── Step 5: Templates ──
    await sidebar.navigateTo('Vorlagen');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/templates/);

    // ── Step 7: Automations ──
    await sidebar.navigateTo('Automationen');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/automations/);

    // ── Step 8: Settings ──
    await sidebar.navigateTo('Einstellungen');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/settings/);
    const settings = new SettingsPage(authenticatedPage);
    await expect(settings.fullNameInput).toHaveValue('Max Mustermann');
  });

  test('user works with folders while browsing inbox', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get(/\/email-accounts\/\d+\/folders/, mockFoldersWithCustom)
      .get(/\/emails/, mockEmailPage)
      .post(/\/email-accounts\/\d+\/folders/, mockFolderCreated);
    await api.apply(authenticatedPage);

    const sidebar = new SidebarPage(authenticatedPage);
    await authenticatedPage.goto('/dashboard');

    // ── Step 1: Expand folders, see custom folders ──
    await sidebar.expandFolders();
    await expect(sidebar.customFolder('Rechnungen')).toBeVisible();
    await expect(sidebar.customFolder('Projekte')).toBeVisible();

    // ── Step 2: Navigate to inbox via direct URL ──
    await authenticatedPage.goto('/dashboard/emails');

    // ── Step 3: Inbox loaded ──
    const emails = new EmailsPage(authenticatedPage);
    await expect(emails.emailCards).toHaveCount(10);

    // ── Step 4: Create a new folder while on inbox ──
    await sidebar.expandFolders();
    await sidebar.folderAddBtn.click();
    await sidebar.folderNameInput.fill('Neuer Ordner');
    await sidebar.folderNameInput.press('Enter');
    await expect(sidebar.folderNameInput).not.toBeVisible();
  });

  test('user opens AI chat while on categories page', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get('/api/v1/categories', mockCategories)
      .get('/api/v1/ai/conversations', []);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/categories');
    const categories = new CategoriesPage(authenticatedPage);

    // ── Step 1: Verify categories loaded ──
    await expect(categories.categoryCards).toHaveCount(2);

    // ── Step 2: Open AI chat panel ──
    await authenticatedPage.locator('button[aria-label="AI Assistant"]').click();
    const chat = new AiChatPage(authenticatedPage);
    await expect(chat.panel).toBeVisible();

    // ── Step 3: Chat input should be visible ──
    await expect(chat.messageInput).toBeVisible();

    // ── Step 4: Close chat ──
    await chat.closeButton.click();
    await expect(chat.panel).not.toBeVisible();

    // ── Step 5: Categories should still be visible behind ──
    await expect(categories.categoryCards).toHaveCount(2);
  });
});
