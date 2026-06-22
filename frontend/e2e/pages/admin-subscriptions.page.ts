import { Locator, Page } from '@playwright/test';

export class AdminSubscriptionsPage {
  readonly kpis: Locator;
  readonly plans: Locator;
  readonly table: Locator;
  readonly rows: Locator;
  readonly search: Locator;
  readonly newPlanBtn: Locator;
  readonly modal: Locator;
  readonly modalClose: Locator;
  readonly editor: Locator;

  constructor(private page: Page) {
    this.kpis = page.locator('[data-testid="admin-psub-kpis"]');
    this.plans = page.locator('[data-testid="admin-psub-plans"]');
    this.table = page.locator('[data-testid="admin-psub-table"]');
    this.rows = page.locator('[data-testid="admin-psub-row"]');
    this.search = page.locator('[data-testid="admin-psub-search"]');
    this.newPlanBtn = page.locator('[data-testid="admin-psub-new-plan"]');
    this.modal = page.locator('[data-testid="admin-psub-modal"]');
    this.modalClose = page.locator('[data-testid="admin-psub-modal-close"]');
    this.editor = page.locator('[data-testid="admin-psub-editor"]');
  }
}
