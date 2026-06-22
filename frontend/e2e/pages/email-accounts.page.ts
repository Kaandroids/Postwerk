import { Locator, Page } from '@playwright/test';

export class EmailAccountsPage {
  readonly addButton: Locator;
  readonly accountRows: Locator;
  readonly backButton: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;
  readonly testImapButton: Locator;
  readonly testSmtpButton: Locator;

  constructor(private page: Page) {
    this.addButton = page.locator('.ek-add-btn');
    this.accountRows = page.locator('.ek-row');
    this.backButton = page.locator('.ek-back-btn');
    this.saveButton = page.locator('[data-testid="ek-save-btn"]');
    this.cancelButton = page.locator('[data-testid="ek-cancel-btn"]');
    this.testImapButton = page.locator('[data-testid="test-imap-btn"]');
    this.testSmtpButton = page.locator('[data-testid="test-smtp-btn"]');
  }

  /** "Kontoname" input — first input in the KONTO fieldset */
  displayNameInput(): Locator {
    return this.page.locator('.ek-field').filter({ hasText: /Kontoname|Account name/i }).locator('.ek-input');
  }

  /** "E-Mail-Adresse" input — use type=email to disambiguate */
  emailInput(): Locator {
    return this.page.locator('.ek-input[type="email"]');
  }

  accountRow(index: number): Locator {
    return this.accountRows.nth(index);
  }

  editButton(index: number): Locator {
    return this.accountRow(index).locator('.ek-icon-btn').first();
  }

  deleteButton(index: number): Locator {
    return this.accountRow(index).locator('.ek-icon-danger');
  }
}
