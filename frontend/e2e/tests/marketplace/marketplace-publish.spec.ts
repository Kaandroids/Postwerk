import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { MarketplacePublishPage } from '../../pages/marketplace.page';
import { mockAutomations } from '../../mocks/automation.mocks';
import { mockPublicDetail } from '../../mocks/marketplace.mocks';

/** Source automation detail exposing two constants for the publishable picker. */
const sourceDetail = {
  ...mockAutomations[0],
  nodes: [],
  edges: [],
  constants: [
    { name: 'API_KEY', value: '', type: 'secret', hasValue: false, description: '' },
    { name: 'THRESHOLD', value: '10', type: 'number', hasValue: true, description: '' },
  ],
};

function applyPublishMocks(api: MockApi): void {
  api
    .get(/\/automations\/1(\?|$)/, sourceDetail)
    .get(/\/automations(\?|$)/, mockAutomations);
}

test.describe('Marketplace — Publish', () => {
  let pg: MarketplacePublishPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    pg = new MarketplacePublishPage(authenticatedPage);
    const api = new MockApi();
    applyPublishMocks(api);
    await api.apply(authenticatedPage);
    await pg.goto();
  });

  test('renders the form with the author automations select', async () => {
    await expect(pg.root).toBeVisible();
    await expect(pg.source.locator('option[value="1"]')).toHaveCount(1);
  });

  test('validation blocks submit when required fields are empty', async ({ authenticatedPage }) => {
    await pg.submit.click();
    await expect(authenticatedPage.locator('.mk-field-error').first()).toBeVisible();
    await expect(authenticatedPage).toHaveURL(/marketplace\/publish/);
  });

  test('publishing a PUBLIC listing navigates to its detail', async ({ authenticatedPage }) => {
    const api = new MockApi();
    applyPublishMocks(api);
    api.post(/\/marketplace\/listings(\?|$)/, mockPublicDetail);
    await api.apply(authenticatedPage);

    await pg.source.selectOption('1');
    await pg.name.fill('My Listing');
    await pg.tagline.fill('Does useful things');
    await pg.category.selectOption('sales');
    await pg.visPublic.click();
    await pg.pricingModel('FREE').click();
    await pg.submit.click();
    await expect(authenticatedPage).toHaveURL(/marketplace\/detail\/list-pub-1/);
  });

  test('rich-text description shows a toolbar and a live preview', async () => {
    await expect(pg.richEditor).toBeVisible();
    await expect(pg.descPreview).not.toBeVisible();
    await pg.description.click();
    await pg.description.pressSequentially('A detailed description');
    await expect(pg.descPreview).toBeVisible();
    await expect(pg.descPreview.locator('.mk-rich')).toContainText('A detailed description');
  });

  test('the share-KB-entries toggle can be enabled', async () => {
    await expect(pg.shareKb).toBeVisible();
    await pg.shareKb.click();
    await expect(pg.shareKb.locator('app-icon')).toBeVisible();
  });

  test('publishing a PUBLIC listing with KB sharing enabled navigates to detail', async ({ authenticatedPage }) => {
    const api = new MockApi();
    applyPublishMocks(api);
    api.post(/\/marketplace\/listings(\?|$)/, mockPublicDetail);
    await api.apply(authenticatedPage);

    await pg.source.selectOption('1');
    await pg.name.fill('Shared KB Listing');
    await pg.tagline.fill('Ships its knowledge base');
    await pg.category.selectOption('sales');
    await pg.visPublic.click();
    await pg.pricingModel('FREE').click();
    await pg.shareKb.click();
    await pg.submit.click();
    await expect(authenticatedPage).toHaveURL(/marketplace\/detail\/list-pub-1/);
  });

  test('PRIVATE visibility reveals the publishable-constants picker', async () => {
    await pg.source.selectOption('1');
    await pg.visPrivate.click();
    await expect(pg.pcPicker).toBeVisible();
    await pg.publishableBox('API_KEY').click();
    await expect(pg.publishableBox('API_KEY').locator('app-icon')).toBeVisible();
  });
});
