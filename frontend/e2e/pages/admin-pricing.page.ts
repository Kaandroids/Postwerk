import { Locator, Page } from '@playwright/test';

export class AdminPricingPage {
  readonly rows: Locator;
  readonly table: Locator;
  readonly createBtn: Locator;
  readonly editButtons: Locator;
  readonly deleteButtons: Locator;
  readonly form: Locator;
  readonly modelInput: Locator;
  readonly inputRate: Locator;
  readonly outputRate: Locator;
  readonly saveBtn: Locator;
  readonly cancelBtn: Locator;

  constructor(private page: Page) {
    this.table = page.locator('[data-testid="pricing-table"]');
    this.rows = page.locator('[data-testid="pricing-row"]');
    this.createBtn = page.locator('[data-testid="pricing-create-btn"]');
    this.editButtons = page.locator('[data-testid="pricing-edit-btn"]');
    this.deleteButtons = page.locator('[data-testid="pricing-delete-btn"]');
    this.form = page.locator('[data-testid="pricing-form"]');
    this.modelInput = page.locator('[data-testid="pricing-model-input"]');
    this.inputRate = page.locator('[data-testid="pricing-input-rate"]');
    this.outputRate = page.locator('[data-testid="pricing-output-rate"]');
    this.saveBtn = page.locator('[data-testid="pricing-save-btn"]');
    this.cancelBtn = page.locator('[data-testid="pricing-cancel-btn"]');
  }
}
