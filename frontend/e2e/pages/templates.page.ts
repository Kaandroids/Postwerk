import { Locator, Page } from '@playwright/test';

export class TemplatesPage {
  readonly addButton: Locator;
  readonly templateCards: Locator;
  readonly emptyState: Locator;
  readonly backButton: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;

  // Form fields
  readonly nameInput: Locator;
  readonly subjectInput: Locator;
  readonly bodyEditor: Locator;
  readonly htmlModeTab: Locator;
  readonly visualModeTab: Locator;
  readonly parameterChips: Locator;

  constructor(private page: Page) {
    this.addButton = page.locator('.tpl-add-btn');
    this.templateCards = page.locator('.tpl-card');
    this.emptyState = page.locator('app-empty-state');
    this.backButton = page.locator('[data-testid="tpl-back-btn"]');
    this.saveButton = page.locator('[data-testid="tpl-save-btn"]');
    this.cancelButton = page.locator('[data-testid="tpl-cancel-btn"]');

    this.nameInput = page.locator('#tpl-name');
    this.subjectInput = page.locator('#tpl-subject');
    this.bodyEditor = page.locator('.tpl-textarea-html, .ProseMirror');
    this.htmlModeTab = page.locator('.tpl-mode-tab').filter({ hasText: 'HTML' });
    this.visualModeTab = page.locator('.tpl-mode-tab').first();
    this.parameterChips = page.locator('.tpl-param-chip');
  }

  cardName(index: number): Locator {
    return this.templateCards.nth(index).locator('.tpl-card-name');
  }

  editButton(index: number): Locator {
    return this.templateCards.nth(index).locator('[data-testid="tpl-edit-btn"]');
  }

  deleteButton(index: number): Locator {
    return this.templateCards.nth(index).locator('.tpl-icon-btn-danger');
  }
}
