import { Page, Locator } from '@playwright/test';

export class SecretsPage {
  readonly page: Page;
  readonly createBtn: Locator;
  readonly cards: Locator;
  readonly form: Locator;
  readonly nameInput: Locator;
  readonly valueInput: Locator;
  readonly saveBtn: Locator;
  readonly cancelBtn: Locator;
  readonly editBtns: Locator;
  readonly deleteBtns: Locator;
  readonly versionBadges: Locator;

  constructor(page: Page) {
    this.page = page;
    this.createBtn = page.locator('[data-testid="secret-create-btn"]');
    this.cards = page.locator('[data-testid="secret-card"]');
    this.form = page.locator('[data-testid="secret-form"]');
    this.nameInput = page.locator('[data-testid="secret-name-input"]');
    this.valueInput = page.locator('[data-testid="secret-value-input"]');
    this.saveBtn = page.locator('[data-testid="secret-save-btn"]');
    this.cancelBtn = page.locator('[data-testid="secret-cancel-btn"]');
    this.editBtns = page.locator('[data-testid="secret-edit-btn"]');
    this.deleteBtns = page.locator('[data-testid="secret-delete-btn"]');
    this.versionBadges = page.locator('[data-testid="secret-version"]');
  }

  card(index: number): Locator {
    return this.cards.nth(index);
  }

  async navigate() {
    await this.page.goto('/dashboard/secrets');
    await this.page.waitForLoadState('networkidle');
  }
}
