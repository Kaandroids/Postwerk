import { test, expect } from '../../fixtures/test-fixtures';
import { EmailsPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockEmailPage, mockEmailDetail, mockCategories } from '../../mocks';

test.describe('Email Detail', () => {
  let emailsPage: EmailsPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    emailsPage = new EmailsPage(authenticatedPage);

    // Register detail route FIRST (Playwright LIFO — last registered = first checked)
    // This specific route handles /emails/{id} without query params
    await authenticatedPage.route(/\/email-accounts\/\d+\/emails\/\d+$/, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockEmailDetail),
        });
      } else {
        await route.fallback();
      }
    });

    const api = new MockApi();
    api
      .get(/\/email-accounts\/\d+\/emails\?/, mockEmailPage)
      .get('/api/v1/categories', mockCategories)
      .patch(/\/emails\/\d+\/read/, { ...mockEmailDetail, isRead: true })
      .patch(/\/emails\/\d+\/star/, { ...mockEmailDetail, isStarred: true });
    await api.apply(authenticatedPage);

    await authenticatedPage.goto('/dashboard/emails');
    // Wait for email list to load
    await expect(emailsPage.emailCards.first()).toBeVisible();
  });

  test('should expand email on click', async () => {
    await emailsPage.emailCard(0).locator('.ib-card-row').click();
    await expect(emailsPage.expandedBody()).toBeVisible();
  });

  test('should show email body content', async () => {
    await emailsPage.emailCard(0).locator('.ib-card-row').click();
    await expect(emailsPage.expandedBody()).toContainText('HTML body');
  });

  test('should show attachments after expanding', async () => {
    await emailsPage.emailCard(0).locator('.ib-card-row').click();
    await expect(emailsPage.expandedBody()).toBeVisible();
    await expect(emailsPage.attachmentList()).toBeVisible();
  });

  test('should show action buttons on expanded email', async ({ authenticatedPage }) => {
    await emailsPage.emailCard(0).locator('.ib-card-row').click();
    await expect(emailsPage.expandedBody()).toBeVisible();
    // Action bar should show Markieren button
    const starBtn = authenticatedPage.locator('.ib-action-btn').filter({ hasText: /Markieren/i });
    await expect(starBtn).toBeVisible();
  });
});
