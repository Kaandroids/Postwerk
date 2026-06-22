import { Locator, Page } from '@playwright/test';

export class RegisterPage {
  readonly nameInput: Locator;
  readonly emailInput: Locator;
  readonly companyInput: Locator;
  readonly phoneInput: Locator;
  readonly passwordInput: Locator;
  readonly confirmInput: Locator;
  readonly termsCheckbox: Locator;
  readonly marketingCheckbox: Locator;
  readonly submitButton: Locator;
  readonly loginLink: Locator;
  readonly errorBanner: Locator;
  readonly strengthMeter: Locator;

  constructor(private page: Page) {
    this.nameInput = page.locator('#reg-name');
    this.emailInput = page.locator('#reg-email');
    this.companyInput = page.locator('#reg-company');
    this.phoneInput = page.locator('#reg-phone');
    this.passwordInput = page.locator('app-password-input[name="password"] input');
    this.confirmInput = page.locator('app-password-input[name="confirm"] input');
    this.termsCheckbox = page.locator('app-checkbox[name="terms"]');
    this.marketingCheckbox = page.locator('app-checkbox[name="marketing"]');
    this.submitButton = page.locator('button[type="submit"]');
    this.loginLink = page.locator('a[href*="login"]');
    this.errorBanner = page.locator('app-error-banner');
    this.strengthMeter = page.locator('app-strength-meter');
  }

  async goto() {
    await this.page.goto('/auth/register');
  }

  async register(data: {
    name: string;
    email: string;
    password: string;
    confirm: string;
    company?: string;
    phone?: string;
  }) {
    await this.nameInput.fill(data.name);
    await this.emailInput.fill(data.email);
    if (data.company) await this.companyInput.fill(data.company);
    if (data.phone) await this.phoneInput.fill(data.phone);
    await this.passwordInput.fill(data.password);
    await this.confirmInput.fill(data.confirm);
    await this.termsCheckbox.click();
    await this.submitButton.click();
  }
}
