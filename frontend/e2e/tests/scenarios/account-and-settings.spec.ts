/**
 * Scenario: Account Management & Settings
 *
 * User navigates to settings, verifies profile data,
 * checks language options, and browses email accounts.
 */
import { test, expect } from '../../fixtures/test-fixtures';
import { SidebarPage, SettingsPage, EmailAccountsPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockUser, mockEmailAccounts } from '../../mocks';

test.describe('Scenario: Account & Settings Management', () => {
  test('user navigates to settings and verifies profile data', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.patch(/\/users\/me/, { ...mockUser, fullName: 'Max Updated' });
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard');
    const sidebar = new SidebarPage(authenticatedPage);

    // ── Step 1: User sees their name ──
    await expect(sidebar.userName).toContainText('Max Mustermann');

    // ── Step 2: Navigate to settings ──
    await sidebar.navigateTo('Einstellungen');
    await expect(authenticatedPage).toHaveURL(/\/dashboard\/settings/);

    // ── Step 3: Verify profile data is loaded ──
    const settings = new SettingsPage(authenticatedPage);
    await expect(settings.fullNameInput).toHaveValue('Max Mustermann');
    await expect(settings.emailInput).toHaveValue('test@example.com');
    await expect(settings.companyInput).toHaveValue('Postwerk GmbH');
    await expect(settings.phoneInput).toHaveValue('+49 170 1234567');

    // ── Step 4: Update profile name ──
    await settings.fullNameInput.clear();
    await settings.fullNameInput.fill('Max Updated');
    await settings.profileSaveButton.click();

    // ── Step 5: Check language cards exist ──
    await expect(settings.langCard('de')).toBeVisible();
    await expect(settings.langCard('en')).toBeVisible();

    // ── Step 6: Check privacy toggles ──
    await expect(settings.marketingToggle).toBeVisible();
    await expect(settings.analyticsToggle).toBeVisible();
  });

  test('user views email accounts list', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.get('/api/v1/email-accounts', mockEmailAccounts);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/email-accounts');
    const accounts = new EmailAccountsPage(authenticatedPage);

    // ── Step 1: See existing accounts ──
    await expect(accounts.accountRows).toHaveCount(2);

    // ── Step 2: Click add to open form ──
    await accounts.addButton.click();

    // ── Step 3: Form should be visible ──
    await expect(accounts.saveButton).toBeVisible();

    // ── Step 4: Cancel and go back to list ──
    await accounts.cancelButton.click();
    await expect(accounts.accountRows).toHaveCount(2);
  });
});
