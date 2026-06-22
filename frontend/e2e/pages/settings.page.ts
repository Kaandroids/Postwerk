import { Locator, Page } from '@playwright/test';

export class SettingsPage {
  // Profile section
  readonly fullNameInput: Locator;
  readonly emailInput: Locator;
  readonly companyInput: Locator;
  readonly phoneInput: Locator;
  readonly profileSaveButton: Locator;

  // Password section
  readonly currentPasswordInput: Locator;
  readonly newPasswordInput: Locator;
  readonly confirmPasswordInput: Locator;
  readonly passwordSaveButton: Locator;
  readonly strengthMeter: Locator;

  // Language section
  readonly langCards: Locator;

  // Privacy toggles
  readonly marketingToggle: Locator;
  readonly analyticsToggle: Locator;

  constructor(private page: Page) {
    this.fullNameInput = page.locator('[data-testid="set-fullname"]');
    this.emailInput = page.locator('[data-testid="set-email"]');
    this.companyInput = page.locator('[data-testid="set-company"]');
    this.phoneInput = page.locator('[data-testid="set-phone"]');
    this.profileSaveButton = page.locator('[data-testid="set-profile-save"]');

    this.currentPasswordInput = page.locator('[data-testid="set-current-pw"]');
    this.newPasswordInput = page.locator('[data-testid="set-new-pw"]');
    this.confirmPasswordInput = page.locator('[data-testid="set-confirm-pw"]');
    this.passwordSaveButton = page.locator('[data-testid="set-pw-save"]');
    this.strengthMeter = page.locator('.set-pass-meter');

    this.langCards = page.locator('.set-lang-card');

    this.marketingToggle = page.locator('[data-testid="set-marketing-toggle"]');
    this.analyticsToggle = page.locator('[data-testid="set-analytics-toggle"]');
  }

  langCard(lang: 'de' | 'en'): Locator {
    return this.langCards.filter({
      hasText: lang === 'de' ? 'Deutsch' : 'English',
    });
  }
}
