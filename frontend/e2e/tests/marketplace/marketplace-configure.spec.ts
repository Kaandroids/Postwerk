import { test, expect } from '../../fixtures/test-fixtures';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { MarketplaceConfigurePage } from '../../pages/marketplace.page';
import { mockLibrary, mockPrivateDetail, mockPrivateAcquisition } from '../../mocks/marketplace.mocks';

function applyConfigureMocks(api: MockApi): void {
  api
    .get(/\/marketplace\/library/, mockLibrary)
    .get(/\/marketplace\/listings\/list-prv-1(\?|$)/, mockPrivateDetail);
}

test.describe('Marketplace — Configure (PRIVATE install)', () => {
  let pg: MarketplaceConfigurePage;

  test.beforeEach(async ({ authenticatedPage }) => {
    pg = new MarketplaceConfigurePage(authenticatedPage);
    const api = new MockApi();
    applyConfigureMocks(api);
    await api.apply(authenticatedPage);
    await pg.goto(mockPrivateAcquisition.id);
  });

  test('renders only the publishable constants and email accounts', async () => {
    await expect(pg.root).toBeVisible();
    await expect(pg.constants).toBeVisible();
    await expect(pg.constantInput('API_KEY')).toBeVisible();
    await expect(pg.constantInput('THRESHOLD')).toBeVisible();
    await expect(pg.accounts).toBeVisible();
  });

  test('secret constant is masked and can be revealed', async () => {
    await expect(pg.constantInput('API_KEY')).toHaveAttribute('type', 'password');
    await pg.reveal('API_KEY').click();
    await expect(pg.constantInput('API_KEY')).toHaveAttribute('type', 'text');
  });

  test('activate is disabled until an account is bound', async () => {
    await expect(pg.activate).toBeDisabled();
    await pg.account(1).click();
    await expect(pg.account(1)).toHaveClass(/on/);
    await expect(pg.activate).toBeEnabled();
  });

  test('save persists constants and account bindings', async ({ authenticatedPage }) => {
    const api = new MockApi();
    applyConfigureMocks(api);
    api.put(/\/marketplace\/acquisitions\/acq-prv-1\/constants/, {});
    api.put(/\/marketplace\/acquisitions\/acq-prv-1\/accounts/, {});
    await api.apply(authenticatedPage);

    await pg.constantInput('THRESHOLD').fill('25');
    await pg.account(1).click();
    await pg.save.click();
    await expect(authenticatedPage.locator('.mk-toast')).toBeVisible();
  });

  test('activate saves, binds and navigates to the library', async ({ authenticatedPage }) => {
    const api = new MockApi();
    applyConfigureMocks(api);
    api.put(/\/marketplace\/acquisitions\/acq-prv-1\/constants/, {});
    api.put(/\/marketplace\/acquisitions\/acq-prv-1\/accounts/, {});
    api.post(/\/marketplace\/acquisitions\/acq-prv-1\/activate/, mockPrivateAcquisition);
    await api.apply(authenticatedPage);

    await pg.account(1).click();
    await pg.activate.click();
    await expect(authenticatedPage).toHaveURL(/marketplace-library/);
  });
});
