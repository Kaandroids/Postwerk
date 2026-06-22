import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { MarketplaceDetailPage } from '../../pages/marketplace.page';
import {
  mockPublicDetail,
  mockPrivateDetail,
  mockPublicListing,
  mockPrivateListing,
  mockPublicAcquisition,
  mockPrivateAcquisition,
  mockReviews,
  mockOwnReviews,
} from '../../mocks/marketplace.mocks';

test.describe('Marketplace — Detail (PUBLIC)', () => {
  let pg: MarketplaceDetailPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    pg = new MarketplaceDetailPage(authenticatedPage);
    const api = new MockApi();
    api.get(/\/marketplace\/listings\/list-pub-1(\?|$)/, mockPublicDetail);
    await api.apply(authenticatedPage);
    await pg.goto(mockPublicListing.id);
  });

  test('shows the node-flow preview and no locked panel', async () => {
    await expect(pg.root).toBeVisible();
    await expect(pg.nodeFlow).toBeVisible();
    await expect(pg.locked).not.toBeVisible();
    await expect(pg.installBtn).toBeVisible();
  });

  test('renders the description as formatted HTML', async () => {
    await expect(pg.description).toBeVisible();
    await expect(pg.description.locator('strong')).toHaveText('forwards');
  });

  test('install → confirm → editor for an editable (PUBLIC) copy', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.get(/\/marketplace\/listings\/list-pub-1(\?|$)/, mockPublicDetail);
    api.post(/\/marketplace\/listings\/list-pub-1\/install/, mockPublicAcquisition);
    await api.apply(authenticatedPage);

    await pg.installBtn.click();
    await expect(pg.confirm).toBeVisible();
    await pg.confirmInstall.click();
    await expect(authenticatedPage).toHaveURL(/automations\/auto-200\/edit/);
  });
});

test.describe('Marketplace — Detail (PRIVATE)', () => {
  let pg: MarketplaceDetailPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    pg = new MarketplaceDetailPage(authenticatedPage);
    const api = new MockApi();
    api.get(/\/marketplace\/listings\/list-prv-1(\?|$)/, mockPrivateDetail);
    await api.apply(authenticatedPage);
    await pg.goto(mockPrivateListing.id);
  });

  test('shows the locked panel and hides the node-flow', async () => {
    await expect(pg.locked).toBeVisible();
    await expect(pg.nodeFlow).not.toBeVisible();
  });

  test('install → confirm → configure for a hidden (PRIVATE) copy', async ({ authenticatedPage }) => {
    const api = new MockApi();
    api.get(/\/marketplace\/listings\/list-prv-1(\?|$)/, mockPrivateDetail);
    api.post(/\/marketplace\/listings\/list-prv-1\/install/, mockPrivateAcquisition);
    await api.apply(authenticatedPage);

    await pg.installBtn.click();
    await expect(pg.confirm).toBeVisible();
    await pg.confirmInstall.click();
    await expect(authenticatedPage).toHaveURL(/marketplace\/configure\/acq-prv-1/);
  });
});

test.describe('Marketplace — Detail (owned, review)', () => {
  test('an owner can submit a review', async ({ authenticatedPage }) => {
    const pg = new MarketplaceDetailPage(authenticatedPage);
    const ownedDetail = {
      ...mockPublicDetail,
      listing: { ...mockPublicListing, owned: true },
    };
    const api = new MockApi();
    api.get(/\/marketplace\/listings\/list-pub-1(\?|$)/, ownedDetail);
    api.post(/\/marketplace\/listings\/list-pub-1\/reviews/, mockReviews[0]);
    await api.apply(authenticatedPage);
    await pg.goto(mockPublicListing.id);

    await expect(pg.reviewCompose).toBeVisible();
    // The composer's submit is disabled until both a rating and text are present.
    await expect(pg.reviewSubmit).toBeDisabled();
    await pg.reviewStar(4).click();
    await pg.reviewText.fill('Works great.');
    await expect(pg.reviewSubmit).toBeEnabled();
    await pg.reviewSubmit.click();
    // Re-load fires after submit; the form stays mounted for an owner.
    await expect(pg.reviewCompose).toBeVisible();
  });

  test('own review is surfaced with a "Deine" badge', async ({ authenticatedPage }) => {
    const pg = new MarketplaceDetailPage(authenticatedPage);
    const ownedDetail = {
      ...mockPublicDetail,
      listing: { ...mockPublicListing, owned: true },
      reviews: mockOwnReviews,
    };
    const api = new MockApi();
    api.get(/\/marketplace\/listings\/list-pub-1(\?|$)/, ownedDetail);
    await api.apply(authenticatedPage);
    await pg.goto(mockPublicListing.id);

    await expect(pg.reviewBadge).toHaveCount(1);
    await expect(pg.reviewBadge).toBeVisible();
  });
});
