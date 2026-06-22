import { Locator, Page } from '@playwright/test';

export class LoginPage {
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly submitButton: Locator;
  readonly rememberCheckbox: Locator;
  readonly registerLink: Locator;
  readonly resetPasswordLink: Locator;
  readonly errorBanner: Locator;

  constructor(private page: Page) {
    this.emailInput = page.locator('#login-email');
    this.passwordInput = page.locator('app-password-input input');
    this.submitButton = page.locator('button[type="submit"]');
    this.rememberCheckbox = page.locator('app-checkbox[name="remember"]');
    this.registerLink = page.locator('a[href*="register"]');
    this.resetPasswordLink = page.locator('a[href*="reset-password"]');
    this.errorBanner = page.locator('app-error-banner');
  }

  async goto() {
    await this.page.goto('/auth/login');
  }

  async login(email: string, password: string) {
    await this.emailInput.fill(email);
    await this.passwordInput.fill(password);
    await this.submitButton.click();
  }
}
