/**
 * Scenario: Login Flows
 *
 * Tests various login scenarios: wrong credentials → retry, login → dashboard,
 * and navigation between auth pages.
 */
import { test, expect } from '@playwright/test';
import { LoginPage, RegisterPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { dismissCookieConsent } from '../../fixtures/auth.fixture';
import {
  mockLoginResponse,
  mockRegisterResponse,
  mockUser,
  mockEmailAccounts,
  mockFolders,
} from '../../mocks';

test.describe('Scenario: Login Flows', () => {
  test('user fails login, then succeeds and reaches dashboard', async ({ page }) => {
    await dismissCookieConsent(page);
    const loginPage = new LoginPage(page);
    await loginPage.goto();

    // ── Step 1: Wrong credentials ──
    const errorApi = new MockApi();
    errorApi.post('/api/v1/auth/login', { message: 'Invalid credentials' }, 401);
    await errorApi.apply(page);

    await loginPage.login('test@example.com', 'wrongpassword');
    await expect(loginPage.errorBanner).toBeVisible();

    // ── Step 2: Correct credentials ──
    const successApi = new MockApi();
    successApi
      .post('/api/v1/auth/login', mockLoginResponse)
      .get('/api/v1/users/me', mockUser)
      .get('/api/v1/email-accounts', mockEmailAccounts)
      .get(/\/folders/, mockFolders);
    await successApi.apply(page);

    await loginPage.login('test@example.com', 'Password123!');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('user navigates login → register → login', async ({ page }) => {
    await dismissCookieConsent(page);
    const loginPage = new LoginPage(page);
    await loginPage.goto();

    // ── Step 1: Go to register ──
    await loginPage.registerLink.click();
    await expect(page).toHaveURL(/\/auth\/register/);

    // ── Step 2: See register form ──
    const registerPage = new RegisterPage(page);
    await expect(registerPage.nameInput).toBeVisible();
    await expect(registerPage.emailInput).toBeVisible();

    // ── Step 3: Go back to login ──
    await registerPage.loginLink.click();
    await expect(page).toHaveURL(/\/auth\/login/);

    // ── Step 4: Go to reset password ──
    await loginPage.resetPasswordLink.click();
    await expect(page).toHaveURL(/\/auth\/reset-password/);
  });
});
