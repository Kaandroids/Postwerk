import { Locator, Page } from '@playwright/test';

export class AdminSystemHealthPage {
  readonly kpis: Locator;
  readonly grid: Locator;
  readonly cards: Locator;
  readonly alert: Locator;
  readonly refresh: Locator;
  readonly maintenance: Locator;
  readonly modal: Locator;
  readonly modalClose: Locator;

  constructor(private page: Page) {
    this.kpis = page.locator('[data-testid="admin-sh-kpis"]');
    this.grid = page.locator('[data-testid="admin-sh-grid"]');
    this.cards = page.locator('[data-testid="admin-sh-card"]');
    this.alert = page.locator('[data-testid="admin-sh-alert"]');
    this.refresh = page.locator('[data-testid="admin-sh-refresh"]');
    this.maintenance = page.locator('[data-testid="admin-sh-maintenance"]');
    this.modal = page.locator('[data-testid="admin-sh-modal"]');
    this.modalClose = page.locator('[data-testid="admin-sh-modal-close"]');
  }
}
