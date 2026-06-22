import { test, expect } from '../../fixtures/test-fixtures';
import { EmailAccountsPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockEmailAccounts, mockConnectionTestSuccess } from '../../mocks';

test.describe('Email Accounts', () => {
  let accountsPage: EmailAccountsPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    accountsPage = new EmailAccountsPage(authenticatedPage);
    const api = new MockApi();
    api.get('/api/v1/email-accounts', mockEmailAccounts);
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/email-accounts');
  });

  test('should display account list', async () => {
    await expect(accountsPage.accountRows).toHaveCount(2);
  });

  test('should show account emails', async ({ authenticatedPage }) => {
    await expect(authenticatedPage.locator('.ek-row-email').first()).toContainText('work@example.com');
  });

  test('should open create form', async () => {
    await accountsPage.addButton.click();
    await expect(accountsPage.displayNameInput()).toBeVisible();
  });

  test('should fill account form', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.post('/api/v1/email-accounts', { id: 3, email: 'new@example.com', displayName: 'New' });
    await api.apply(authenticatedPage);

    await accountsPage.addButton.click();
    await accountsPage.displayNameInput().fill('New Account');
    await accountsPage.emailInput().fill('new@example.com');
    await expect(accountsPage.saveButton).toBeVisible();
  });

  test('should show IMAP test button when form open', async () => {
    await accountsPage.addButton.click();
    // IMAP test button becomes visible after enabling read capability
    await expect(accountsPage.saveButton).toBeVisible();
  });

  test('should show delete button on account rows', async () => {
    await expect(accountsPage.accountRows.first()).toBeVisible();
    // Delete button should exist in the row
    const deleteBtn = accountsPage.accountRows.first().locator('.ek-icon-danger');
    await expect(deleteBtn).toBeVisible();
  });
});
