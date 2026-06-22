import { Locator, Page } from '@playwright/test';

export class AdminGdprPage {
  readonly kpis: Locator;
  readonly retention: Locator;
  readonly alert: Locator;
  readonly table: Locator;
  readonly rows: Locator;
  readonly search: Locator;
  readonly refresh: Locator;
  readonly newRequest: Locator;
  readonly modal: Locator;
  readonly modalClose: Locator;
  readonly createModal: Locator;

  constructor(private page: Page) {
    this.kpis = page.locator('[data-testid="admin-gdpr-kpis"]');
    this.retention = page.locator('[data-testid="admin-gdpr-retention"]');
    this.alert = page.locator('[data-testid="admin-gdpr-alert"]');
    this.table = page.locator('[data-testid="admin-gdpr-table"]');
    this.rows = page.locator('[data-testid="admin-gdpr-row"]');
    this.search = page.locator('[data-testid="admin-gdpr-search"]');
    this.refresh = page.locator('[data-testid="admin-gdpr-refresh"]');
    this.newRequest = page.locator('[data-testid="admin-gdpr-new"]');
    this.modal = page.locator('[data-testid="admin-gdpr-modal"]');
    this.modalClose = page.locator('[data-testid="admin-gdpr-modal-close"]');
    this.createModal = page.locator('[data-testid="admin-gdpr-create-modal"]');
  }
}
