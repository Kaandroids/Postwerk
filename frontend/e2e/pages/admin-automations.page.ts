import { Locator, Page } from '@playwright/test';

export class AdminAutomationsPage {
  readonly statTotal: Locator;
  readonly statSuccess: Locator;
  readonly statFailed: Locator;
  readonly statActive: Locator;
  readonly successBar: Locator;
  readonly topTable: Locator;
  readonly execTable: Locator;

  constructor(private page: Page) {
    this.statTotal = page.locator('[data-testid="auto-stat-total"]');
    this.statSuccess = page.locator('[data-testid="auto-stat-success"]');
    this.statFailed = page.locator('[data-testid="auto-stat-failed"]');
    this.statActive = page.locator('[data-testid="auto-stat-active"]');
    this.successBar = page.locator('[data-testid="auto-success-bar"]');
    this.topTable = page.locator('[data-testid="auto-top-table"]');
    this.execTable = page.locator('[data-testid="auto-exec-table"]');
  }
}
