import { test, expect } from '../../fixtures/test-fixtures';
import { EmailsPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockEmailPage, mockSyncResult, mockCategories } from '../../mocks';

test.describe('Email Sync', () => {
  test('should display email list on emails page', async ({ authenticatedPage }) => {
    const emailsPage = new EmailsPage(authenticatedPage);
    const api = new MockApi();
    api
      .get(/\/email-accounts\/\d+\/emails/, mockEmailPage)
      .get('/api/v1/categories', mockCategories)
      .post(/\/emails\/sync/, mockSyncResult);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/emails');
    await expect(emailsPage.emailCards.first()).toBeVisible();
    await expect(emailsPage.emailCards).toHaveCount(10);
  });

  test('should show page title', async ({ authenticatedPage }) => {
    const emailsPage = new EmailsPage(authenticatedPage);
    const api = new MockApi();
    api
      .get(/\/email-accounts\/\d+\/emails/, mockEmailPage)
      .get('/api/v1/categories', mockCategories);
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/emails');
    await expect(emailsPage.pageTitle).toBeVisible();
  });
});
