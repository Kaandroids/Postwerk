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

  test('should register and show the verification prompt', async ({ page }) => {
    const api = new MockApi();
    api.post('/api/v1/auth/register', mockRegisterResponse);
    await api.apply(page);

    await registerPage.register({
      name: 'Test User',
      email: 'new@example.com',
      password: 'StrongPass123!',
      confirm: 'StrongPass123!',
    });
    // Registration no longer logs in — the user must confirm their email first.
    await expect(registerPage.checkEmail).toBeVisible();
  });

  test('should navigate to login page', async ({ page }) => {
    await registerPage.loginLink.click();
    await expect(page).toHaveURL(/\/auth\/login/);
  });
});
