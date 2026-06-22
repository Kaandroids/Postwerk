import { test, expect } from '../../fixtures/test-fixtures';
import { EmailsPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockEmailPage, mockEmailPageTwo, mockCategories } from '../../mocks';

test.describe('Inbox', () => {
  let emailsPage: EmailsPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    emailsPage = new EmailsPage(authenticatedPage);
    const api = new MockApi();
    api
      .get(/\/email-accounts\/\d+\/emails/, mockEmailPage)
      .get('/api/v1/categories', mockCategories);
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/emails');
  });

  test('should display email list', async () => {
    await expect(emailsPage.emailCards.first()).toBeVisible();
    await expect(emailsPage.emailCards).toHaveCount(10);
  });

  test('should show email subject and sender', async () => {
    await expect(emailsPage.emailSubject(0)).toContainText('Test Email Subject');
    await expect(emailsPage.emailSender(0)).toContainText('Sender');
  });

  test('should search emails with debounce', async ({ authenticatedPage }) => {
    const searchResults = {
      ...mockEmailPage,
      content: mockEmailPage.content.slice(0, 2),
      totalElements: 2,
      totalPages: 1,
    };
    // Override the mock for search queries
    const api = new MockApi();
    api
      .get(/\/email-accounts\/\d+\/emails/, searchResults)
      .get('/api/v1/categories', mockCategories);
    await api.apply(authenticatedPage);

    await emailsPage.search('Bestellung');
    // Wait for debounce
    await authenticatedPage.waitForTimeout(500);
    await expect(emailsPage.emailCards).toHaveCount(2);
  });

  test('should paginate emails', async ({ authenticatedPage }) => {
    // First page is already loaded, set up page 2 mock
    const api = new MockApi();
    api
      .get(/\/email-accounts\/\d+\/emails/, mockEmailPageTwo)
      .get('/api/v1/categories', mockCategories);
    await api.apply(authenticatedPage);

    await expect(emailsPage.nextButton).toBeVisible();
    await emailsPage.nextButton.click();
    await expect(emailsPage.emailCards).toHaveCount(10);
  });

  test('should display filter dropdowns', async () => {
    await expect(emailsPage.filterDropdown('Datum')).toBeVisible();
    await expect(emailsPage.filterDropdown('Kategorie')).toBeVisible();
    await expect(emailsPage.filterDropdown('Status')).toBeVisible();
  });
});
