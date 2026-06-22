import { Locator, Page } from '@playwright/test';

export class PlansPage {
  readonly cycleSwitch: Locator;
  readonly monthlyBtn: Locator;
  readonly yearlyBtn: Locator;
  readonly banner: Locator;
  readonly bannerName: Locator;
  readonly bannerUpgrade: Locator;
  readonly usageBars: Locator;
  readonly planCards: Locator;
  readonly planCardBadges: Locator;
  readonly planCardCtas: Locator;
  readonly addonCards: Locator;
  readonly invoiceRows: Locator;
  readonly bannerPaymentChip: Locator;
  readonly bannerPriceNum: Locator;
  readonly bannerPriceUnit: Locator;

  constructor(private page: Page) {
    this.cycleSwitch = page.locator('[data-testid="pl-cycle-switch"]');
    this.monthlyBtn = page.locator('[data-testid="pl-cycle-monthly"]');
    this.yearlyBtn = page.locator('[data-testid="pl-cycle-yearly"]');
    this.banner = page.locator('[data-testid="pl-banner"]');
    this.bannerName = page.locator('[data-testid="pl-banner-name"]');
    this.bannerUpgrade = page.locator('[data-testid="pl-banner-upgrade"]');
    this.usageBars = page.locator('[data-testid="pl-usage-bar"]');
    this.planCards = page.locator('[data-testid="plan-card"]');
    this.planCardBadges = page.locator('[data-testid="plan-card-badge"]');
    this.planCardCtas = page.locator('[data-testid="plan-card-cta"]');
    this.addonCards = page.locator('[data-testid="pl-addon-card"]');
    this.invoiceRows = page.locator('[data-testid="pl-invoice-row"]');
    this.bannerPaymentChip = page.locator('.pl-pm-chip');
    this.bannerPriceNum = page.locator('.pl-banner-price-num');
    this.bannerPriceUnit = page.locator('.pl-banner-price-unit');
  }

  planCardByIndex(index: number): Locator {
    return this.planCards.nth(index);
  }

  planCardPrice(index: number): Locator {
    return this.planCards.nth(index).locator('.pl-card-price-num');
  }

  planCardPriceUnit(index: number): Locator {
    return this.planCards.nth(index).locator('.pl-card-price-unit');
  }

  invoiceAmount(index: number): Locator {
    return this.invoiceRows.nth(index).locator('.pl-invoice-amount');
  }

  invoiceDate(index: number): Locator {
    return this.invoiceRows.nth(index).locator('.pl-invoice-date');
  }

  invoiceStatus(index: number): Locator {
    return this.invoiceRows.nth(index).locator('.pl-status');
  }

  invoiceDownloadBtn(index: number): Locator {
    return this.invoiceRows.nth(index).locator('.pl-icon-btn');
  }
}
