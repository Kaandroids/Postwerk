import { Locator, Page } from '@playwright/test';

export class CategoriesPage {
  readonly addButton: Locator;
  readonly categoryCards: Locator;
  readonly emptyState: Locator;
  readonly backButton: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;

  // Form fields
  readonly nameInput: Locator;
  readonly descriptionInput: Locator;
  readonly positiveExampleInput: Locator;
  readonly negativeExampleInput: Locator;
  readonly colorSwatches: Locator;

  constructor(private page: Page) {
    this.addButton = page.locator('.cat-add-btn');
    this.categoryCards = page.locator('.cat-card');
    this.emptyState = page.locator('app-empty-state');
    this.backButton = page.locator('[data-testid="cat-back-btn"]');
    this.saveButton = page.locator('[data-testid="cat-save-btn"]');
    this.cancelButton = page.locator('[data-testid="cat-cancel-btn"]');

    this.nameInput = page.locator('#cat-name');
    this.descriptionInput = page.locator('#cat-desc');
    this.positiveExampleInput = page.locator('#cat-pos');
    this.negativeExampleInput = page.locator('#cat-neg');
    this.colorSwatches = page.locator('.cp-swatch');
  }

  cardName(index: number): Locator {
    return this.categoryCards.nth(index).locator('.cat-card-name');
  }

  editButton(index: number): Locator {
    return this.categoryCards.nth(index).locator('[data-testid="cat-edit-btn"]');
  }

  deleteButton(index: number): Locator {
    return this.categoryCards.nth(index).locator('.cat-icon-btn-danger');
  }
}
