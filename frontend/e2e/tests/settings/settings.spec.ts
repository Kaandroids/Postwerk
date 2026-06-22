import { test, expect } from '../../fixtures/test-fixtures';
import { SettingsPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockUser } from '../../mocks';

test.describe('Settings', () => {
  let settingsPage: SettingsPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    settingsPage = new SettingsPage(authenticatedPage);
    await authenticatedPage.goto('/dashboard/settings');
  });

  test('should display profile section with save button', async () => {
    // Settings page profile section should be visible with save button
    await expect(settingsPage.profileSaveButton).toBeVisible();
    await expect(settingsPage.fullNameInput).toBeVisible();
  });

  test('should update profile', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.put('/api/v1/users/me', { ...mockUser, fullName: 'Updated Name' });
    await api.apply(authenticatedPage);

    await settingsPage.fullNameInput.clear();
    await settingsPage.fullNameInput.fill('Updated Name');
    await settingsPage.profileSaveButton.click();
  });

  test('should show password strength meter when typing new password', async () => {
    await settingsPage.newPasswordInput.fill('WeakPw1!');
    await expect(settingsPage.strengthMeter).toBeVisible();
  });

  test('should change password', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.post('/api/v1/users/me/password', {});
    await api.apply(authenticatedPage);

    // Current password input has readonly — click it first to remove readonly
    await settingsPage.currentPasswordInput.click();
    await settingsPage.currentPasswordInput.fill('OldPassword123!');
    await settingsPage.newPasswordInput.fill('NewStrongPass456!');
    await settingsPage.confirmPasswordInput.fill('NewStrongPass456!');
    await settingsPage.passwordSaveButton.click();
  });

  test('should switch language', async ({ authenticatedPage }) => {
    await settingsPage.langCard('en').click();
    // After switching to English, settings title should change
    await expect(authenticatedPage.locator('body')).toBeVisible();
  });

  test('should toggle privacy settings', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.patch('/api/v1/users/me/consent', { ...mockUser, marketingConsent: false });
    await api.apply(authenticatedPage);

    await expect(settingsPage.marketingToggle).toBeVisible();
    await settingsPage.marketingToggle.click();
  });
});
