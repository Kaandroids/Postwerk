import { Locator, Page } from '@playwright/test';

export class AdminBackgroundJobsPage {
  readonly kpis: Locator;
  readonly queues: Locator;
  readonly alert: Locator;
  readonly table: Locator;
  readonly rows: Locator;
  readonly search: Locator;
  readonly refresh: Locator;
  readonly modal: Locator;
  readonly modalClose: Locator;

  constructor(private page: Page) {
    this.kpis = page.locator('[data-testid="admin-bj-kpis"]');
    this.queues = page.locator('[data-testid="admin-bj-queues"]');
    this.alert = page.locator('[data-testid="admin-bj-alert"]');
    this.table = page.locator('[data-testid="admin-bj-table"]');
    this.rows = page.locator('[data-testid="admin-bj-row"]');
    this.search = page.locator('[data-testid="admin-bj-search"]');
    this.refresh = page.locator('[data-testid="admin-bj-refresh"]');
    this.modal = page.locator('[data-testid="admin-bj-modal"]');
    this.modalClose = page.locator('[data-testid="admin-bj-modal-close"]');
  }
}
