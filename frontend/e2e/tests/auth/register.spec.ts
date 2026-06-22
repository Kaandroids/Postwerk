import { test, expect } from '@playwright/test';
import { RegisterPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { dismissCookieConsent } from '../../fixtures/auth.fixture';
import { mockRegisterResponse } from '../../mocks';

test.describe('Register', () => {
  let registerPage: RegisterPage;

  test.beforeEach(async ({ page }) => {
    await dismissCookieConsent(page);
    registerPage = new RegisterPage(page);
    await registerPage.goto();
  });

  test('should show validation for empty required fields', async ({ page }) => {
    await registerPage.submitButton.click();
    await expect(page).toHaveURL(/\/auth\/register/);
  });

  test('should show password strength meter', async () => {
    await registerPage.passwordInput.fill('weak');
    await expect(registerPage.strengthMeter).toBeVisible();
  });

  test('should require terms acceptance', async ({ page }) => {
    await registerPage.nameInput.fill('Test User');
    await registerPage.emailInput.fill('new@example.com');
    await registerPage.passwordInput.fill('StrongPass123!');
    await registerPage.confirmInput.fill('StrongPass123!');
    // Don't check terms
    await registerPage.submitButton.click();
    await expect(page).toHaveURL(/\/auth\/register/);
  });

  test('should register successfully', async ({ page }) => {
    const api = new MockApi();
    api
      .post('/api/v1/auth/register', mockRegisterResponse)
      .get('/api/v1/users/me', { id: 1, email: 'new@example.com', fullName: 'Test User' })
      .get('/api/v1/email-accounts', [])
      .get(/\/folders/, []);
    await api.apply(page);

    await registerPage.register({
      name: 'Test User',
      email: 'new@example.com',
      password: 'StrongPass123!',
      confirm: 'StrongPass123!',
    });
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('should navigate to login page', async ({ page }) => {
    await registerPage.loginLink.click();
    await expect(page).toHaveURL(/\/auth\/login/);
  });
});
