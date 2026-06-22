import { Locator, Page } from '@playwright/test';

export class AdminAnnouncementsPage {
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
    this.kpis = page.locator('[data-testid="admin-ann-kpis"]');
    this.alert = page.locator('[data-testid="admin-ann-alert"]');
    this.table = page.locator('[data-testid="admin-ann-table"]');
    this.rows = page.locator('[data-testid="admin-ann-row"]');
    this.search = page.locator('[data-testid="admin-ann-search"]');
    this.refresh = page.locator('[data-testid="admin-ann-refresh"]');
    this.newBtn = page.locator('[data-testid="admin-ann-new"]');
    this.modal = page.locator('[data-testid="admin-ann-modal"]');
    this.modalClose = page.locator('[data-testid="admin-ann-modal-close"]');
  }
}
