import { type Locator, type Page } from '@playwright/test';

/** Page object for the redesigned typed automation constants modal. */
export class AutomationConstantsPage {
  readonly page: Page;

  readonly openButton: Locator;
  readonly modal: Locator;
  readonly empty: Locator;
  readonly addButton: Locator;
  readonly doneButton: Locator;
  readonly closeButton: Locator;

  readonly rows: Locator;
  readonly editButtons: Locator;
  readonly removeButtons: Locator;
  readonly tokens: Locator;
  readonly usageButtons: Locator;

  // inline editor
  readonly editor: Locator;
  readonly nameInput: Locator;
  readonly valueInput: Locator;
  readonly descInput: Locator;
  readonly commitButton: Locator;
  readonly editorCancel: Locator;

  constructor(page: Page) {
    this.page = page;
    this.openButton = page.locator('[data-testid="automation-constants-btn"]');
    this.modal = page.locator('[data-testid="constants-modal"]');
    this.empty = page.locator('[data-testid="constants-empty"]');
    this.addButton = page.locator('[data-testid="constants-add"]');
    this.doneButton = page.locator('[data-testid="constants-cancel"]');
    this.closeButton = page.locator('[data-testid="constants-close"]');

    this.rows = page.locator('[data-testid="constants-row"]');
    this.editButtons = page.locator('[data-testid="constants-edit"]');
    this.removeButtons = page.locator('[data-testid="constants-remove"]');
    this.tokens = page.locator('[data-testid="constants-token"]');
    this.usageButtons = page.locator('[data-testid="constants-usage"]');

    this.editor = page.locator('[data-testid="constants-editor"]');
    this.nameInput = page.locator('[data-testid="constants-name-input"]');
    this.valueInput = page.locator('[data-testid="constants-value-input"]');
    this.descInput = page.locator('[data-testid="constants-desc-input"]');
    this.commitButton = page.locator('[data-testid="constants-save"]');
    this.editorCancel = page.locator('[data-testid="constants-edit-cancel"]');
  }

  async open(): Promise<void> {
    await this.openButton.click();
  }

  /** Selects a type in the inline editor's segmented control. */
  typeButton(type: 'text' | 'number' | 'boolean' | 'url' | 'secret'): Locator {
    return this.page.locator(`[data-testid="constants-type-${type}"]`);
  }
}
