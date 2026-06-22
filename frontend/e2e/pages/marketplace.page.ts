import { type Locator, type Page } from '@playwright/test';

/** Page object for the marketplace Discover surface. */
export class MarketplaceDiscoverPage {
  readonly page: Page;
  readonly root: Locator;
  readonly publishCta: Locator;
  readonly search: Locator;
  readonly grid: Locator;
  readonly cards: Locator;
  readonly empty: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.locator('[data-testid="marketplace-discover"]');
    this.publishCta = page.locator('[data-testid="mk-publish-cta"]');
    this.search = page.locator('[data-testid="mk-search"]');
    this.grid = page.locator('[data-testid="mk-grid"]');
    this.cards = page.locator('[data-testid="mk-grid"] app-market-card');
    this.empty = page.locator('[data-testid="mk-empty"]');
  }

  sort(key: string): Locator {
    return this.page.locator(`[data-testid="mk-sort-${key}"]`);
  }
  category(key: string): Locator {
    return this.page.locator(`[data-testid="mk-cat-${key}"]`);
  }

  async goto(): Promise<void> {
    await this.page.goto('/dashboard/marketplace');
  }
}

/** Page object for the marketplace Detail surface. */
export class MarketplaceDetailPage {
  readonly page: Page;
  readonly root: Locator;
  readonly back: Locator;
  readonly locked: Locator;
  readonly nodeFlow: Locator;
  readonly buybox: Locator;
  readonly installBtn: Locator;
  readonly configureBtn: Locator;
  readonly openBtn: Locator;
  readonly confirm: Locator;
  readonly confirmCancel: Locator;
  readonly confirmInstall: Locator;
  readonly reviewText: Locator;
  readonly reviewSubmit: Locator;
  readonly reviewCompose: Locator;
  readonly reviewBadge: Locator;
  readonly description: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.locator('[data-testid="marketplace-detail"]');
    this.back = page.locator('[data-testid="mk-back"]');
    this.locked = page.locator('[data-testid="mk-locked"]');
    this.nodeFlow = page.locator('[data-testid="mk-node-flow"]');
    this.buybox = page.locator('[data-testid="mk-buybox"]');
    this.installBtn = page.locator('[data-testid="mk-install"]');
    this.configureBtn = page.locator('[data-testid="mk-configure"]');
    this.openBtn = page.locator('[data-testid="mk-open"]');
    this.confirm = page.locator('[data-testid="mk-confirm"]');
    this.confirmCancel = page.locator('[data-testid="mk-confirm-cancel"]');
    this.confirmInstall = page.locator('[data-testid="mk-confirm-install"]');
    this.reviewText = page.locator('[data-testid="mk-review-text"]');
    this.reviewSubmit = page.locator('[data-testid="mk-review-submit"]');
    this.reviewCompose = page.locator('[data-testid="mk-review-compose"]');
    this.reviewBadge = page.locator('.mk-review-badge');
    this.description = page.locator('.mk-d-body.mk-rich');
  }

  reviewStar(n: number): Locator {
    return this.page.locator(`[data-testid="mk-review-star-${n}"]`);
  }

  async goto(id: string): Promise<void> {
    await this.page.goto(`/dashboard/marketplace/detail/${id}`);
  }
}

/** Page object for the marketplace Publish surface. */
export class MarketplacePublishPage {
  readonly page: Page;
  readonly root: Locator;
  readonly source: Locator;
  readonly name: Locator;
  readonly tagline: Locator;
  readonly description: Locator;
  readonly category: Locator;
  readonly visPublic: Locator;
  readonly visPrivate: Locator;
  readonly price: Locator;
  readonly pcPicker: Locator;
  readonly shareKb: Locator;
  readonly submit: Locator;
  readonly richEditor: Locator;
  readonly descPreview: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.locator('[data-testid="marketplace-publish"]');
    this.source = page.locator('[data-testid="mk-source"]');
    this.name = page.locator('[data-testid="mk-name"]');
    this.tagline = page.locator('[data-testid="mk-tagline"]');
    this.description = page.locator('[data-testid="mk-rt-editor"]');
    this.category = page.locator('[data-testid="mk-category"]');
    this.visPublic = page.locator('[data-testid="mk-vis-public"]');
    this.visPrivate = page.locator('[data-testid="mk-vis-private"]');
    this.price = page.locator('[data-testid="mk-price"]');
    this.pcPicker = page.locator('[data-testid="mk-pc"]');
    this.shareKb = page.locator('[data-testid="mk-share-kb"]');
    this.submit = page.locator('[data-testid="mk-submit"]');
    this.richEditor = page.locator('[data-testid="mk-rich-text"]');
    this.descPreview = page.locator('[data-testid="mk-desc-preview"]');
  }

  pricingModel(pm: string): Locator {
    return this.page.locator(`[data-testid="mk-pm-${pm}"]`);
  }
  publishableBox(name: string): Locator {
    return this.page.locator(`[data-testid="mk-pc-box-${name}"]`);
  }

  async goto(): Promise<void> {
    await this.page.goto('/dashboard/marketplace/publish');
  }
}

/** Page object for the marketplace Configure surface (PRIVATE installs). */
export class MarketplaceConfigurePage {
  readonly page: Page;
  readonly root: Locator;
  readonly back: Locator;
  readonly constants: Locator;
  readonly accounts: Locator;
  readonly save: Locator;
  readonly activate: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.locator('[data-testid="marketplace-configure"]');
    this.back = page.locator('[data-testid="mk-back"]');
    this.constants = page.locator('[data-testid="mk-cv"]');
    this.accounts = page.locator('[data-testid="mk-accounts"]');
    this.save = page.locator('[data-testid="mk-save"]');
    this.activate = page.locator('[data-testid="mk-activate"]');
  }

  constantInput(name: string): Locator {
    return this.page.locator(`[data-testid="mk-cv-input-${name}"]`);
  }
  reveal(name: string): Locator {
    return this.page.locator(`[data-testid="mk-cv-reveal-${name}"]`);
  }
  account(id: string | number): Locator {
    return this.page.locator(`[data-testid="mk-acc-${id}"]`);
  }

  async goto(acqId: string): Promise<void> {
    await this.page.goto(`/dashboard/marketplace/configure/${acqId}`);
  }
}

/** Page object for the marketplace Library surface. */
export class MarketplaceLibraryPage {
  readonly page: Page;
  readonly root: Locator;
  readonly tabInstalled: Locator;
  readonly tabPurchased: Locator;
  readonly tabPublished: Locator;
  readonly list: Locator;
  readonly rows: Locator;
  readonly browse: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.locator('[data-testid="marketplace-library"]');
    this.tabInstalled = page.locator('[data-testid="mk-tab-installed"]');
    this.tabPurchased = page.locator('[data-testid="mk-tab-purchased"]');
    this.tabPublished = page.locator('[data-testid="mk-tab-published"]');
    this.list = page.locator('[data-testid="mk-lib-list"]');
    this.rows = page.locator('[data-testid="mk-lib-list"] .mk-lib-row');
    this.browse = page.locator('[data-testid="mk-browse"]');
  }

  unpublish(id: string): Locator {
    return this.page.locator(`[data-testid="mk-unpublish-${id}"]`);
  }
  manage(id: string): Locator {
    return this.page.locator(`[data-testid="mk-manage-${id}"]`);
  }

  async goto(): Promise<void> {
    await this.page.goto('/dashboard/marketplace-library');
  }
}
