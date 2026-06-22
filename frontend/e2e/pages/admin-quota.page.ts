import { Locator, Page } from '@playwright/test';

export class AdminQuotaPage {
  readonly kpis: Locator;
  readonly table: Locator;
  readonly rows: Locator;
  readonly newBtn: Locator;
  readonly search: Locator;
  readonly modal: Locator;
  readonly targetPicker: Locator;
  readonly targetSearch: Locator;
  readonly kindSeg: Locator;
  readonly amountInput: Locator;
  readonly reasonInput: Locator;
  readonly saveBtn: Locator;
  readonly cancelBtn: Locator;
  readonly modalClose: Locator;

  constructor(private page: Page) {
    this.kpis = page.locator('[data-testid="admin-quota-kpis"]');
    this.table = page.locator('[data-testid="admin-quota-table"]');
    this.rows = page.locator('[data-testid="admin-quota-row"]');
    this.newBtn = page.locator('[data-testid="admin-quota-new-btn"]');
    this.search = page.locator('[data-testid="admin-quota-search"]');
    this.modal = page.locator('[data-testid="quota-modal"]');
    this.targetPicker = page.locator('[data-testid="quota-target-picker"]');
    this.targetSearch = page.locator('[data-testid="quota-target-search"]');
    this.kindSeg = page.locator('[data-testid="quota-kind-seg"]');
    this.amountInput = page.locator('[data-testid="quota-amount-input"]');
    this.reasonInput = page.locator('[data-testid="quota-reason-input"]');
    this.saveBtn = page.locator('[data-testid="quota-save-btn"]');
    this.cancelBtn = page.locator('[data-testid="quota-cancel-btn"]');
    this.modalClose = page.locator('[data-testid="quota-modal-close"]');
  }
}
