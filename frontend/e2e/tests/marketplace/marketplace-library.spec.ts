import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { MarketplaceLibraryPage } from '../../pages/marketplace.page';
import { mockLibrary } from '../../mocks/marketplace.mocks';

test.describe('Marketplace — Library', () => {
  let pg: MarketplaceLibraryPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    pg = new MarketplaceLibraryPage(authenticatedPage);
    const api = new MockApi();
    api.get(/\/marketplace\/library/, mockLibrary);
    await api.apply(authenticatedPage);
    await pg.goto();
  });

  test('installed tab shows an editable install with an Open action', async ({ authenticatedPage }) => {
    await expect(pg.root).toBeVisible();
    await expect(pg.tabInstalled).toHaveClass(/on/);
    await expect(pg.rows).toHaveCount(mockLibrary.installed.length);
    await expect(authenticatedPage.locator('[data-testid="mk-open-acq-pub-1"]')).toBeVisible();
  });

  test('purchased tab shows a hidden install with a Configure action', async ({ authenticatedPage }) => {
    await pg.tabPurchased.click();
    await expect(pg.rows).toHaveCount(mockLibrary.purchased.length);
    await expect(authenticatedPage.locator('[data-testid="mk-configure-acq-prv-1"]')).toBeVisible();
  });

  test('published tab shows authored listings with manage + unpublish', async () => {
    await pg.tabPublished.click();
    await expect(pg.rows).toHaveCount(mockLibrary.published.length);
    await expect(pg.manage('list-mine-1')).toBeVisible();
    await expect(pg.unpublish('list-mine-1')).toBeVisible();
  });

  test('unpublish confirms then calls the delete endpoint', async ({ authenticatedPage }) => {
    // First library load returns the authored listing; the reload after unpublish returns none.
    let libCalls = 0;
    const api = new MockApi();
    api.handle('GET', /\/marketplace\/library/, async (route) => {
      const body = libCalls++ === 0 ? mockLibrary : { ...mockLibrary, published: [] };
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    });
    api.delete(/\/marketplace\/listings\/list-mine-1/, {});
    await api.apply(authenticatedPage);
    authenticatedPage.on('dialog', (d) => d.accept());
    await pg.goto();

    await pg.tabPublished.click();
    await pg.unpublish('list-mine-1').click();
    await expect(pg.rows).toHaveCount(0);
  });
});
