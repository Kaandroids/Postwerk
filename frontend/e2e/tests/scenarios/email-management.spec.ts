/**
 * Scenario: Email Management Workflow
 *
 * User navigates inbox, reads emails, paginates,
 * and creates folders to organize.
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { SidebarPage, EmailsPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import {
  mockEmailPage,
  mockEmailPageTwo,
  mockEmailDetail,
  mockFolderCreated,
} from '../../mocks';

test.describe('Scenario: Email Management', () => {
  test('user reads email with attachments and syncs', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get(/\/emails\/\d+/, mockEmailDetail)
      .get(/\/emails/, mockEmailPage);
    await api.apply(authenticatedPage);

    // ── Step 1: Navigate to inbox ──
    await authenticatedPage.goto('/dashboard/emails');
    const emails = new EmailsPage(authenticatedPage);

    // ── Step 2: Verify inbox loaded ──
    await expect(emails.emailCards).toHaveCount(10);
    await expect(emails.pageSubtitle).toBeVisible();

    // ── Step 3: Click on email to read details ──
    await emails.emailCard(0).click();
    await expect(emails.expandedBody()).toBeVisible();

    // ── Step 4: Verify attachments are shown ──
    await expect(emails.attachmentList()).toBeVisible();
  });

  test('user paginates through inbox pages', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api
      .get(/\/emails\?.*page=1/, mockEmailPageTwo)
      .get(/\/emails/, mockEmailPage);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/emails');
    const emails = new EmailsPage(authenticatedPage);

    // Page 1
    await expect(emails.emailCards).toHaveCount(10);
    await expect(emails.pageIndicator).toContainText('1');

    // Go to page 2
    await emails.nextButton.click();
    await expect(emails.emailCards).toHaveCount(10);
    await expect(emails.pageIndicator).toContainText('2');

    // Go back to page 1
    await emails.prevButton.click();
    await expect(emails.emailCards).toHaveCount(10);
    await expect(emails.pageIndicator).toContainText('1');
  });

  test('user creates a folder and sees it in sidebar', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.post(/\/email-accounts\/\d+\/folders/, mockFolderCreated);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard');
    const sidebar = new SidebarPage(authenticatedPage);

    // ── Step 1: Expand folders section ──
    await sidebar.expandFolders();

    // ── Step 2: See empty state ──
    await expect(sidebar.folderEmptyState).toBeVisible();

    // ── Step 3: Click add folder ──
    await sidebar.folderAddBtn.click();
    await expect(sidebar.folderNameInput).toBeVisible();

    // ── Step 4: Type and create ──
    await sidebar.folderNameInput.fill('Neuer Ordner');
    await sidebar.folderNameInput.press('Enter');

    // ── Step 5: Input closes after creation ──
    await expect(sidebar.folderNameInput).not.toBeVisible();
  });
});
