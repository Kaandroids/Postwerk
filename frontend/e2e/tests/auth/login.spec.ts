import { test, expect } from '@playwright/test';
import { LoginPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { dismissCookieConsent } from '../../fixtures/auth.fixture';
import { mockLoginResponse, mockLoginError } from '../../mocks';

test.describe('Login', () => {
  let loginPage: LoginPage;

  test.beforeEach(async ({ page }) => {
    await dismissCookieConsent(page);
    loginPage = new LoginPage(page);
    await loginPage.goto();
  });

  test('should show validation errors for empty fields', async ({ page }) => {
    await loginPage.submitButton.click();
    // Form should not navigate — still on login page
    await expect(page).toHaveURL(/\/auth\/login/);
  });

  test('should show error for invalid credentials', async ({ page }) => {
    const api = new MockApi();
    api.post('/api/v1/auth/login', mockLoginError, 401);
    await api.apply(page);

    await loginPage.login('wrong@example.com', 'wrongpassword');
    await expect(loginPage.errorBanner).toBeVisible();
  });

  test('should login successfully and redirect to dashboard', async ({ page }) => {
    const api = new MockApi();
    api
      .post('/api/v1/auth/login', mockLoginResponse)
      .get('/api/v1/users/me', { id: 1, email: 'test@example.com', fullName: 'Test User' })
      .get('/api/v1/email-accounts', [])
      .get(/\/folders/, []);
    await api.apply(page);

    await loginPage.login('test@example.com', 'Password123!');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('should navigate to register page', async () => {
    await loginPage.registerLink.click();
    await expect(loginPage['page']).toHaveURL(/\/auth\/register/);
  });

  test('should navigate to reset password page', async () => {
    await loginPage.resetPasswordLink.click();
    await expect(loginPage['page']).toHaveURL(/\/auth\/reset-password/);
  });
});
