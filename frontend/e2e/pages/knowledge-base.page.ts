import { Locator, Page } from '@playwright/test';

/** Page object for the Knowledge Base management page (/dashboard/knowledge-bases). */
export class KnowledgeBasePage {
  readonly newButton: Locator;
  readonly cards: Locator;
  readonly emptyState: Locator;

  // Config form
  readonly nameInput: Locator;
  readonly paramsetSelect: Locator;
  readonly embedCheckboxes: Locator;
  readonly saveButton: Locator;

  // Entries
  readonly entryInputs: Locator;
  readonly addEntryButton: Locator;
  readonly importInput: Locator;
  readonly tableRows: Locator;

  constructor(private page: Page) {
    this.newButton = page.locator('[data-testid="kb-new"]');
    this.cards = page.locator('[data-testid="kb-card"]');
    this.emptyState = page.locator('app-empty-state');

    this.nameInput = page.locator('[data-testid="kb-name-input"]');
    this.paramsetSelect = page.locator('[data-testid="kb-paramset-select"]');
    this.embedCheckboxes = page.locator('[data-testid="kb-embed"]');
    this.saveButton = page.locator('[data-testid="kb-save"]');

    this.entryInputs = page.locator('[data-testid="kb-entry-input"]');
    this.addEntryButton = page.locator('[data-testid="kb-entry-add"]');
    this.importInput = page.locator('[data-testid="kb-import-input"]');
    this.tableRows = page.locator('.kb-table tbody tr');
  }

  cardName(index: number): Locator {
    return this.cards.nth(index).locator('.kb-card-head h3');
  }

  entriesButton(index: number): Locator {
    return this.cards.nth(index).locator('[data-testid="kb-entries-btn"]');
  }
}
