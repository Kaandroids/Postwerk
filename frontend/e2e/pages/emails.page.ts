import { Locator, Page } from '@playwright/test';

export class EmailsPage {
  readonly emailList: Locator;
  readonly emailCards: Locator;
  readonly searchInput: Locator;
  readonly pageTitle: Locator;
  readonly pageSubtitle: Locator;
  readonly prevButton: Locator;
  readonly nextButton: Locator;
  readonly pageIndicator: Locator;

  constructor(private page: Page) {
    this.emailList = page.locator('.ib-card').first().locator('..');
    this.emailCards = page.locator('.ib-card');
    this.searchInput = page.locator('.ib-search-input');
    this.pageTitle = page.locator('.dash-title');
    this.pageSubtitle = page.locator('.dash-subtitle');
    this.prevButton = page.locator('[data-testid="prev-page"]');
    this.nextButton = page.locator('[data-testid="next-page"]');
    this.pageIndicator = page.locator('.ib-page-indicator');
  }

  emailCard(index: number): Locator {
    return this.emailCards.nth(index);
  }

  emailSubject(index: number): Locator {
    return this.emailCard(index).locator('.ib-subj');
  }

  emailSender(index: number): Locator {
    return this.emailCard(index).locator('.ib-from');
  }

  starButton(index: number): Locator {
    return this.emailCard(index).locator('.ib-action-btn', { hasText: /star|Stern/i });
  }

  filterDropdown(label: string): Locator {
    return this.page.locator('.ib-dd-trigger').filter({ hasText: label });
  }

  filterPill(label: string): Locator {
    return this.page.locator('.ib-dd-trigger').filter({ hasText: label });
  }

  async search(query: string) {
    await this.searchInput.fill(query);
  }

  expandedBody(): Locator {
    return this.page.locator('.ib-expand-body');
  }

  attachmentList(): Locator {
    return this.page.locator('.ib-attachments');
  }

  syncButton(): Locator {
    return this.page.locator('[data-testid="sync-btn"], .ib-sync-btn, button:has-text("Sync")');
  }
}
