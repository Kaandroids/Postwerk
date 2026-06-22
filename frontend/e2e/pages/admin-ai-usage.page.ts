import { Locator, Page } from '@playwright/test';

export class AdminAiUsagePage {
  readonly statTotal: Locator;
  readonly statPrompt: Locator;
  readonly statOutput: Locator;
  readonly statBillable: Locator;
  readonly statCost: Locator;
  readonly periodSelect: Locator;
  readonly modelBars: Locator;
  readonly userTable: Locator;

  constructor(private page: Page) {
    this.statTotal = page.locator('[data-testid="ai-stat-total"]');
    this.statPrompt = page.locator('[data-testid="ai-stat-prompt"]');
    this.statOutput = page.locator('[data-testid="ai-stat-output"]');
    this.statBillable = page.locator('[data-testid="ai-stat-billable"]');
    this.statCost = page.locator('[data-testid="ai-stat-cost"]');
    this.periodSelect = page.locator('[data-testid="ai-period-select"]');
    this.modelBars = page.locator('[data-testid="ai-model-bar"]');
    this.userTable = page.locator('[data-testid="ai-user-table"]');
  }
}
