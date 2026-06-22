import { Locator, Page } from '@playwright/test';

export class AdminDashboardPage {
  readonly statUsers: Locator;
  readonly statActiveUsers: Locator;
  readonly statExecutions: Locator;
  readonly statEmails: Locator;
  readonly statAiSpend: Locator;
  readonly chart: Locator;
  readonly spendByModel: Locator;
  readonly rangeSelector: Locator;
  readonly refreshBtn: Locator;

  constructor(private page: Page) {
    this.statUsers = page.locator('[data-testid="admin-stat-users"]');
    this.statActiveUsers = page.locator('[data-testid="admin-kpi-active-users"]');
    this.statExecutions = page.locator('[data-testid="admin-kpi-executions"]');
    this.statEmails = page.locator('[data-testid="admin-kpi-emails"]');
    this.statAiSpend = page.locator('[data-testid="admin-kpi-aispend"]');
    this.chart = page.locator('[data-testid="admin-chart"]');
    this.spendByModel = page.locator('[data-testid="admin-spend-by-model"]');
    this.rangeSelector = page.locator('[data-testid="admin-range"]');
    this.refreshBtn = page.locator('[data-testid="admin-refresh"]');
  }
}
