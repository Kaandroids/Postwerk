import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { MarketplaceDiscoverPage } from '../../pages/marketplace.page';
import { mockDiscoverListings } from '../../mocks/marketplace.mocks';

/** Registers a discover endpoint that returns no results when the query contains `zzz`. */
function applyDiscoverMocks(api: MockApi): void {
  api.handle('GET', /\/marketplace\/listings(\?|$)/, async (route) => {
    const empty = route.request().url().includes('q=zzz');
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(empty ? [] : mockDiscoverListings),
    });
  });
}

test.describe('Marketplace — Discover', () => {
  let pg: MarketplaceDiscoverPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    pg = new MarketplaceDiscoverPage(authenticatedPage);
    const api = new MockApi();
    applyDiscoverMocks(api);
    await api.apply(authenticatedPage);
    await pg.goto();
  });

  test('renders the listing grid', async () => {
    await expect(pg.root).toBeVisible();
    await expect(pg.grid).toBeVisible();
    await expect(pg.cards).toHaveCount(mockDiscoverListings.length);
  });

  test('category filter marks the active chip', async () => {
    await pg.category('sales').click();
    await expect(pg.category('sales')).toHaveClass(/on/);
    await expect(pg.cards).toHaveCount(mockDiscoverListings.length);
  });

  test('sort buttons toggle active state', async () => {
    await pg.sort('rating').click();
    await expect(pg.sort('rating')).toHaveClass(/on/);
  });

  test('search with no matches shows the empty state', async () => {
    await pg.search.fill('zzz');
    await expect(pg.empty).toBeVisible();
    await expect(pg.grid).not.toBeVisible();
  });

  test('publish CTA navigates to the publish surface', async ({ authenticatedPage }) => {
    await pg.publishCta.click();
    await expect(authenticatedPage).toHaveURL(/marketplace\/publish/);
  });
});
