import { Locator, Page } from '@playwright/test';

export class AdminPlansPage {
  readonly planCards: Locator;
  readonly createBtn: Locator;
  readonly editButtons: Locator;
  readonly deleteButtons: Locator;
  readonly form: Locator;
  readonly nameInput: Locator;
  readonly saveBtn: Locator;
  readonly cancelBtn: Locator;
  readonly costLimitInput: Locator;

  constructor(private page: Page) {
    this.planCards = page.locator('[data-testid="plan-card"]');
    this.createBtn = page.locator('[data-testid="plan-create-btn"]');
    this.editButtons = page.locator('[data-testid="plan-edit-btn"]');
    this.deleteButtons = page.locator('[data-testid="plan-delete-btn"]');
    this.form = page.locator('[data-testid="plan-form"]');
    this.nameInput = page.locator('[data-testid="plan-name-input"]');
    this.saveBtn = page.locator('[data-testid="plan-save-btn"]');
    this.cancelBtn = page.locator('[data-testid="plan-cancel-btn"]');
    this.costLimitInput = page.locator('[data-testid="plan-cost-limit-input"]');
  }
}
