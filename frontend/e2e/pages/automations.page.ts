import { Locator, Page } from '@playwright/test';

export class AutomationsPage {
  readonly addButton: Locator;
  readonly automationCards: Locator;
  readonly emptyState: Locator;
  readonly backButton: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;

  // Form fields
  readonly nameInput: Locator;
  readonly descriptionInput: Locator;

  constructor(private page: Page) {
    this.addButton = page.locator('.auto-add-btn');
    this.automationCards = page.locator('.auto-card');
    this.emptyState = page.locator('app-empty-state');
    this.backButton = page.locator('[data-testid="auto-back-btn"]');
    this.saveButton = page.locator('[data-testid="auto-save-btn"]');
    this.cancelButton = page.locator('[data-testid="auto-cancel-btn"]');

    this.nameInput = page.locator('#auto-name');
    this.descriptionInput = page.locator('#auto-desc');
  }

  cardName(index: number): Locator {
    return this.automationCards.nth(index).locator('.auto-card-name');
  }

  statusBadge(index: number): Locator {
    return this.automationCards.nth(index).locator('.auto-status-badge');
  }

  statValues(index: number): Locator {
    return this.automationCards.nth(index).locator('.auto-stat-value');
  }

  statLabels(index: number): Locator {
    return this.automationCards.nth(index).locator('.auto-stat-label');
  }

  cardFooter(index: number): Locator {
    return this.automationCards.nth(index).locator('.auto-card-footer');
  }

  toggleButton(index: number): Locator {
    return this.automationCards.nth(index).locator('[data-testid="auto-toggle-btn"]');
  }

  editButton(index: number): Locator {
    return this.automationCards.nth(index).locator('.auto-icon-btn').first();
  }

  deleteButton(index: number): Locator {
    return this.automationCards.nth(index).locator('.auto-icon-btn-danger');
  }
}
