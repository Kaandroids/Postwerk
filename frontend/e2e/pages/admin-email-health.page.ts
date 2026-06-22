import { Locator, Page } from '@playwright/test';

export class AdminEmailHealthPage {
  readonly kpis: Locator;
  readonly clusters: Locator;
  readonly alert: Locator;
  readonly table: Locator;
  readonly rows: Locator;
  readonly search: Locator;
  readonly refresh: Locator;
  readonly modal: Locator;
  readonly modalClose: Locator;

  constructor(private page: Page) {
    this.kpis = page.locator('[data-testid="admin-eh-kpis"]');
    this.clusters = page.locator('[data-testid="admin-eh-clusters"]');
    this.alert = page.locator('[data-testid="admin-eh-alert"]');
    this.table = page.locator('[data-testid="admin-eh-table"]');
    this.rows = page.locator('[data-testid="admin-eh-row"]');
    this.search = page.locator('[data-testid="admin-eh-search"]');
    this.refresh = page.locator('[data-testid="admin-eh-refresh"]');
    this.modal = page.locator('[data-testid="admin-eh-modal"]');
    this.modalClose = page.locator('[data-testid="admin-eh-modal-close"]');
  }
}
