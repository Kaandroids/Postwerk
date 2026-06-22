import { Locator, Page } from '@playwright/test';

export class AdminMarketplacePage {
  readonly kpis: Locator;
  readonly seg: Locator;
  readonly segListings: Locator;
  readonly segReviews: Locator;
  readonly refresh: Locator;
  readonly listingsTable: Locator;
  readonly listingRows: Locator;
  readonly reviewsTable: Locator;
  readonly reviewRows: Locator;
  readonly listingSearch: Locator;
  readonly listingModal: Locator;
  readonly reviewModal: Locator;
  readonly modalClose: Locator;

  constructor(private page: Page) {
    this.kpis = page.locator('[data-testid="admin-mkt-kpis"]');
    this.seg = page.locator('[data-testid="admin-mkt-seg"]');
    this.segListings = page.locator('[data-testid="admin-mkt-seg-listings"]');
    this.segReviews = page.locator('[data-testid="admin-mkt-seg-reviews"]');
    this.refresh = page.locator('[data-testid="admin-mkt-refresh"]');
    this.listingsTable = page.locator('[data-testid="admin-mkt-listings-table"]');
    this.listingRows = page.locator('[data-testid="admin-mkt-listing-row"]');
    this.reviewsTable = page.locator('[data-testid="admin-mkt-reviews-table"]');
    this.reviewRows = page.locator('[data-testid="admin-mkt-review-row"]');
    this.listingSearch = page.locator('[data-testid="admin-mkt-l-search"]');
    this.listingModal = page.locator('[data-testid="admin-mkt-listing-modal"]');
    this.reviewModal = page.locator('[data-testid="admin-mkt-review-modal"]');
    this.modalClose = page.locator('[data-testid="admin-mkt-modal-close"]');
  }
}
