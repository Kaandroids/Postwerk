import { Locator, Page } from '@playwright/test';

export class AdminFeatureFlagsPage {
  readonly kpis: Locator;
  readonly alert: Locator;
  readonly table: Locator;
  readonly rows: Locator;
  readonly search: Locator;
  readonly refresh: Locator;
  readonly newBtn: Locator;
  readonly modal: Locator;
  readonly modalClose: Locator;

  constructor(private page: Page) {
    this.kpis = page.locator('[data-testid="admin-ff-kpis"]');
    this.alert = page.locator('[data-testid="admin-ff-alert"]');
    this.table = page.locator('[data-testid="admin-ff-table"]');
    this.rows = page.locator('[data-testid="admin-ff-row"]');
    this.search = page.locator('[data-testid="admin-ff-search"]');
    this.refresh = page.locator('[data-testid="admin-ff-refresh"]');
    this.newBtn = page.locator('[data-testid="admin-ff-new"]');
    this.modal = page.locator('[data-testid="admin-ff-modal"]');
    this.modalClose = page.locator('[data-testid="admin-ff-modal-close"]');
  }
}
