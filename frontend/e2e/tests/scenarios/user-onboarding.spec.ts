/**
 * Scenario: New User Onboarding Journey
 *
 * A new user registers, lands on the dashboard, explores the sidebar,
 * navigates to settings to customize their profile, and switches language.
 */
import { test, expect } from '@playwright/test';
import { LoginPage, RegisterPage, SidebarPage, SettingsPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { dismissCookieConsent } from '../../fixtures/auth.fixture';
import { mockRegisterResponse, mockUser, mockEmailAccounts, mockFolders } from '../../mocks';

test.describe('Scenario: New User Onboarding', () => {
  test('user registers and explores the dashboard', async ({ page }) => {
    await dismissCookieConsent(page);

    // ── Step 1: User lands on login page, clicks "register" ──
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await expect(page).toHaveURL(/\/auth\/login/);
    await loginPage.registerLink.click();
    await expect(page).toHaveURL(/\/auth\/register/);

    // ── Step 2: User fills in registration form ──
    const registerPage = new RegisterPage(page);

    // Type a weak password first — strength meter should show
    await registerPage.passwordInput.fill('123');
    await expect(registerPage.strengthMeter).toBeVisible();

    // Now fill the full form
    const newUser = { id: 10, email: 'anna@firma.de', fullName: 'Anna Schmidt' };
    const api = new MockApi();
    api
      .post('/api/v1/auth/register', mockRegisterResponse)
      .get('/api/v1/users/me', newUser)
      .get('/api/v1/email-accounts', [])
      .get(/\/folders/, []);
    await api.apply(page);

    await registerPage.nameInput.fill(newUser.fullName);
    await registerPage.emailInput.fill(newUser.email);
    await registerPage.passwordInput.fill('SecurePass99!');
    await registerPage.confirmInput.fill('SecurePass99!');
    await registerPage.termsCheckbox.click();
    await registerPage.submitButton.click();

    // ── Step 3: User lands on dashboard ──
    await expect(page).toHaveURL(/\/dashboard/);

    // ── Step 4: User sees their name in the sidebar ──
    const sidebar = new SidebarPage(page);
    await expect(sidebar.userName).toContainText('Anna Schmidt');

    // ── Step 5: User navigates to settings ──
    await sidebar.navigateTo('Einstellungen');
    await expect(page).toHaveURL(/\/dashboard\/settings/);

    // ── Step 6: User sees their profile data ──
    const settings = new SettingsPage(page);
    await expect(settings.fullNameInput).toHaveValue('Anna Schmidt');
    await expect(settings.emailInput).toHaveValue('anna@firma.de');

    // ── Step 7: User switches language to English ──
    const patchApi = new MockApi();
    patchApi.patch('/api/v1/users/me/language', { language: 'en' });
    await patchApi.apply(page);
    await settings.langCard('en').click();

    // Language card should become active
    await expect(settings.langCard('en')).toHaveAttribute('data-active', '1');
  });

  test('user registers, tries mismatched passwords, then succeeds', async ({ page }) => {
    await dismissCookieConsent(page);
    const registerPage = new RegisterPage(page);
    await registerPage.goto();

    // Fill form with mismatched passwords
    await registerPage.nameInput.fill('Test User');
    await registerPage.emailInput.fill('test@test.com');
    await registerPage.passwordInput.fill('StrongPass123!');
    await registerPage.confirmInput.fill('DifferentPass!');
    await registerPage.termsCheckbox.click();
    await registerPage.submitButton.click();

    // Should stay on register page (validation prevents submission)
    await expect(page).toHaveURL(/\/auth\/register/);

    // Fix the password and register successfully
    const api = new MockApi();
    api
      .post('/api/v1/auth/register', mockRegisterResponse)
      .get('/api/v1/users/me', { id: 1, email: 'test@test.com', fullName: 'Test User' })
      .get('/api/v1/email-accounts', [])
      .get(/\/folders/, []);
    await api.apply(page);

    await registerPage.confirmInput.clear();
    await registerPage.confirmInput.fill('StrongPass123!');
    await registerPage.submitButton.click();
    await expect(page).toHaveURL(/\/dashboard/);
  });
});
