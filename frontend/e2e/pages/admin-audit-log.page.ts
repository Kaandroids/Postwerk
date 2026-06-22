import { Locator, Page } from '@playwright/test';

export class AdminAuditLogPage {
  readonly actionFilter: Locator;
  readonly logTable: Locator;
  readonly exportBtn: Locator;
  readonly prevBtn: Locator;
  readonly nextBtn: Locator;

  constructor(private page: Page) {
    this.actionFilter = page.locator('[data-testid="audit-action-filter"]');
    this.logTable = page.locator('[data-testid="audit-log-table"]');
    this.exportBtn = page.locator('[data-testid="audit-export-btn"]');
    this.prevBtn = page.locator('[data-testid="audit-page-prev"]');
    this.nextBtn = page.locator('[data-testid="audit-page-next"]');
  }
}
