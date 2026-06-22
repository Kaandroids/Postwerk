import { Locator, Page } from '@playwright/test';

export class AutomationTestPanelPage {
  readonly page: Page;
  readonly testPanel: Locator;
  readonly testList: Locator;
  readonly testCards: Locator;
  readonly createButton: Locator;
  readonly runAllButton: Locator;

  // Form
  readonly testForm: Locator;
  readonly nameInput: Locator;
  readonly emailFromInput: Locator;
  readonly emailToInput: Locator;
  readonly emailSubjectInput: Locator;
  readonly emailBodyInput: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;

  // Results
  readonly resultStatusBadge: Locator;
  readonly assertionSummary: Locator;

  constructor(page: Page) {
    this.page = page;
    this.testPanel = page.locator('[data-testid="test-panel"]');
    this.testList = page.locator('[data-testid="test-list"]');
    this.testCards = page.locator('[data-testid="test-card"]');
    this.createButton = page.locator('[data-testid="test-create-btn"]');
    this.runAllButton = page.locator('[data-testid="test-run-all-btn"]');

    this.testForm = page.locator('[data-testid="test-form"]');
    this.nameInput = page.locator('[data-testid="test-name-input"]');
    this.emailFromInput = page.locator('[data-testid="test-email-from"]');
    this.emailToInput = page.locator('[data-testid="test-email-to"]');
    this.emailSubjectInput = page.locator('[data-testid="test-email-subject"]');
    this.emailBodyInput = page.locator('[data-testid="test-email-body"]');
    this.saveButton = page.locator('[data-testid="test-save-btn"]');
    this.cancelButton = page.locator('[data-testid="test-cancel-btn"]');

    this.resultStatusBadge = page.locator('[data-testid="test-result-status-badge"]');
    this.assertionSummary = page.locator('[data-testid="test-assertion-summary"]');
  }

  async openTestPanel(): Promise<void> {
    const panelButton = this.page.locator('[data-testid="editor-tab-tests"]');
    await panelButton.click();
    await this.testPanel.waitFor({ state: 'visible' });
  }

  getTestCards(): Locator {
    return this.testCards;
  }

  async clickCreateTest(): Promise<void> {
    await this.createButton.click();
    await this.testForm.waitFor({ state: 'visible' });
  }

  async fillTestForm(data: {
    name: string;
    from: string;
    to: string;
    subject: string;
    body: string;
  }): Promise<void> {
    await this.nameInput.fill(data.name);
    await this.emailFromInput.fill(data.from);
    await this.emailToInput.fill(data.to);
    await this.emailSubjectInput.fill(data.subject);
    await this.emailBodyInput.fill(data.body);
  }

  async addAssertion(): Promise<void> {
    const addAssertionBtn = this.page.locator('[data-testid="test-add-assertion-btn"]');
    await addAssertionBtn.click();
  }

  async clickSave(): Promise<void> {
    await this.saveButton.click();
  }

  async clickRunAll(): Promise<void> {
    await this.runAllButton.click();
  }

  runButton(index: number): Locator {
    return this.testCards.nth(index).locator('[data-testid="test-run-btn"]');
  }

  deleteButton(index: number): Locator {
    return this.testCards.nth(index).locator('[data-testid="test-delete-btn"]');
  }

  getResultStatus(index: number): Locator {
    return this.testCards.nth(index).locator('[data-testid="test-result-status-badge"]');
  }
}
