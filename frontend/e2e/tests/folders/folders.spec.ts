import { test, expect } from '../../fixtures/test-fixtures';
import { SidebarPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import {
  mockFolders,
  mockFoldersWithCustom,
  mockCustomFolder,
  mockFolderCreated,
} from '../../mocks';

test.describe('Folder Management', () => {
  let sidebar: SidebarPage;

  test.describe('with custom folders', () => {
    test.beforeEach(async ({ authenticatedPage }) => {
      // Override default folders mock with custom folders
      const api = new MockApi();
      api
        .get(/\/api\/v1\/email-accounts\/\d+\/folders/, mockFoldersWithCustom)
        .post(/\/api\/v1\/email-accounts\/\d+\/folders/, mockFolderCreated);
      await api.apply(authenticatedPage);

      sidebar = new SidebarPage(authenticatedPage);
      await authenticatedPage.goto('/dashboard');
      await sidebar.expandFolders();
    });

    test('should display custom folders', async () => {
      await expect(sidebar.customFolder('Rechnungen')).toBeVisible();
      await expect(sidebar.customFolder('Projekte')).toBeVisible();
      await expect(sidebar.customFolders()).toHaveCount(2);
    });

    test('should show unread badge on folder with unread count', async () => {
      const projekte = sidebar.customFolder('Projekte');
      await expect(projekte.locator('.badge')).toHaveText('3');
    });

    test('should not show unread badge when count is zero', async () => {
      const rechnungen = sidebar.customFolder('Rechnungen');
      await expect(rechnungen.locator('.badge')).toHaveCount(0);
    });

    test('should navigate to custom folder on click', async ({ authenticatedPage }) => {
      await sidebar.customFolder('Rechnungen').click();
      await expect(authenticatedPage).toHaveURL(/folder=Rechnungen/);
    });

    test('should show delete button on hover', async ({ authenticatedPage }) => {
      const folder = sidebar.customFolder('Rechnungen');
      await folder.hover();
      await expect(sidebar.folderDeleteBtn('Rechnungen')).toBeVisible();
    });
  });

  test.describe('empty state', () => {
    test.beforeEach(async ({ authenticatedPage }) => {
      sidebar = new SidebarPage(authenticatedPage);
      await authenticatedPage.goto('/dashboard');
      await sidebar.expandFolders();
    });

    test('should show empty state when no custom folders exist', async () => {
      // Default mockFolders has no role=OTHER folders
      await expect(sidebar.folderEmptyState).toBeVisible();
    });
  });

  test.describe('folder creation', () => {
    test.beforeEach(async ({ authenticatedPage }) => {
      const api = new MockApi();
      api.post(/\/api\/v1\/email-accounts\/\d+\/folders/, mockFolderCreated);
      await api.apply(authenticatedPage);

      sidebar = new SidebarPage(authenticatedPage);
      await authenticatedPage.goto('/dashboard');
      await sidebar.expandFolders();
    });

    test('should show input field when add button is clicked', async () => {
      await sidebar.folderAddBtn.click();
      await expect(sidebar.folderNameInput).toBeVisible();
      await expect(sidebar.folderNameInput).toBeFocused();
    });

    test('should create folder on Enter', async () => {
      await sidebar.folderAddBtn.click();
      await sidebar.folderNameInput.fill('Neuer Ordner');
      await sidebar.folderNameInput.press('Enter');
      // Input should disappear after successful creation
      await expect(sidebar.folderNameInput).not.toBeVisible();
    });

    test('should cancel folder creation on Escape', async () => {
      await sidebar.folderAddBtn.click();
      await expect(sidebar.folderNameInput).toBeVisible();
      await sidebar.folderNameInput.press('Escape');
      await expect(sidebar.folderNameInput).not.toBeVisible();
    });

    test('should cancel when submitting empty input', async () => {
      await sidebar.folderAddBtn.click();
      await sidebar.folderNameInput.press('Enter');
      await expect(sidebar.folderNameInput).not.toBeVisible();
    });
  });

  test.describe('folder creation error', () => {
    test.beforeEach(async ({ authenticatedPage }) => {
      const api = new MockApi();
      api.post(
        /\/api\/v1\/email-accounts\/\d+\/folders/,
        { message: 'Folder name contains invalid characters' },
        400
      );
      await api.apply(authenticatedPage);

      sidebar = new SidebarPage(authenticatedPage);
      await authenticatedPage.goto('/dashboard');
      await sidebar.expandFolders();
    });

    test('should show error message for invalid folder name', async () => {
      await sidebar.folderAddBtn.click();
      await sidebar.folderNameInput.fill('Invalid/Name');
      await sidebar.folderNameInput.press('Enter');
      await expect(sidebar.folderError).toBeVisible();
      await expect(sidebar.folderNameInput).toHaveClass(/has-error/);
    });

    test('should keep input open after error', async () => {
      await sidebar.folderAddBtn.click();
      await sidebar.folderNameInput.fill('Bad.Name');
      await sidebar.folderNameInput.press('Enter');
      await expect(sidebar.folderError).toBeVisible();
      await expect(sidebar.folderNameInput).toBeVisible();
    });
  });

  test.describe('folder deletion', () => {
    test.beforeEach(async ({ authenticatedPage }) => {
      const api = new MockApi();
      api
        .get(/\/api\/v1\/email-accounts\/\d+\/folders/, mockFoldersWithCustom)
        .delete(/\/api\/v1\/email-accounts\/\d+\/folders\//, {});
      await api.apply(authenticatedPage);

      sidebar = new SidebarPage(authenticatedPage);
      await authenticatedPage.goto('/dashboard');
      await sidebar.expandFolders();
    });

    test('should remove folder after delete confirmation', async ({ authenticatedPage }) => {
      await expect(sidebar.customFolders()).toHaveCount(2);

      // Click delete on Rechnungen
      await sidebar.customFolder('Rechnungen').hover();
      await sidebar.folderDeleteBtn('Rechnungen').click();

      // Confirm dialog should appear — click the confirm button
      const confirmBtn = authenticatedPage.locator('.btn-confirm');
      await confirmBtn.click();

      // Folder should be removed (optimistic update)
      await expect(sidebar.customFolder('Rechnungen')).not.toBeVisible();
    });
  });
});
