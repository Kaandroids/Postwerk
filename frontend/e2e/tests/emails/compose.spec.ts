import { test, expect } from '../../fixtures/test-fixtures';
import { ComposePanelPage } from '../../pages';
import { MockApi } from '../../fixtures/mock-api.fixture';
import { mockEmailPage, mockCategories, mockComposeResponse, mockDraftResponse, mockDraftEmail, mockDraftAttachment } from '../../mocks';

test.describe('Compose Panel', () => {
  let composePage: ComposePanelPage;

  test.beforeEach(async ({ authenticatedPage }) => {
    composePage = new ComposePanelPage(authenticatedPage);
    const api = new MockApi();
    api
      .get(/\/email-accounts\/\d+\/emails/, mockEmailPage)
      .get('/api/v1/categories', mockCategories)
      .post(/\/email-accounts\/\d+\/emails\/send/, mockComposeResponse)
      .post(/\/email-accounts\/\d+\/emails\/drafts$/, mockDraftResponse)
      .put(/\/email-accounts\/\d+\/emails\/drafts\//, mockDraftResponse)
      .delete(/\/email-accounts\/\d+\/emails\/drafts\//, {})
      .get(/\/drafts\/.*\/attachments$/, [mockDraftAttachment])
      .post(/\/drafts\/.*\/attachments$/, mockDraftAttachment)
      .delete(/\/drafts\/.*\/attachments\//, {})
      .post(/\/email-accounts\/\d+\/emails\/sync/, { newEmailCount: 0, syncedAt: new Date().toISOString() });
    await api.apply(authenticatedPage);
    await authenticatedPage.goto('/dashboard/emails');
  });

  test('should show compose button', async () => {
    await expect(composePage.composeBtn).toBeVisible();
  });

  test('should open compose panel on button click', async () => {
    await composePage.composeBtn.click();
    await expect(composePage.panel).toBeVisible();
    await expect(composePage.toInput).toBeVisible();
    await expect(composePage.subjectInput).toBeVisible();
  });

  test('should close compose panel', async () => {
    await composePage.composeBtn.click();
    await expect(composePage.panel).toBeVisible();
    await composePage.closeBtn.click();
    await expect(composePage.panel).not.toBeVisible();
  });

  test('should close compose panel on backdrop click', async ({ authenticatedPage }) => {
    await composePage.composeBtn.click();
    await expect(composePage.panel).toBeVisible();
    // Click the backdrop (top-left corner which is outside the modal)
    await composePage.backdrop.click({ position: { x: 10, y: 10 } });
    await expect(composePage.panel).not.toBeVisible();
  });

  test('should show CC/BCC fields on toggle', async () => {
    await composePage.composeBtn.click();
    await expect(composePage.ccInput).not.toBeVisible();
    await composePage.ccToggle.click();
    await expect(composePage.ccInput).toBeVisible();
    await expect(composePage.bccInput).toBeVisible();
  });

  test('should send email', async () => {
    await composePage.composeBtn.click();
    await composePage.fillTo('recipient@example.com');
    await composePage.fillSubject('Test Subject');
    await composePage.sendBtn.click();
    // Panel should close after successful send
    await expect(composePage.panel).not.toBeVisible();
  });

  test('should disable send button when fields empty', async () => {
    await composePage.composeBtn.click();
    await expect(composePage.sendBtn).toBeDisabled();
  });

  test('should enable send button when to and subject filled', async () => {
    await composePage.composeBtn.click();
    await composePage.fillTo('test@example.com');
    await composePage.fillSubject('Test');
    await expect(composePage.sendBtn).toBeEnabled();
  });

  test('should open reply with pre-filled fields', async ({ authenticatedPage }) => {
    // First expand an email
    const firstCard = authenticatedPage.locator('.ib-card').first();
    await firstCard.click();
    // Wait for email detail to load
    const api = new MockApi();
    api.get(/\/email-accounts\/\d+\/emails\/\d+/, {
      ...mockEmailPage.content[0],
      bodyText: 'Hello world',
      bodyHtml: '<p>Hello world</p>',
      automationTraces: [],
    });
    await api.apply(authenticatedPage);
    await firstCard.click();
    await authenticatedPage.waitForTimeout(500);

    // Click reply
    await composePage.replyBtn.click();
    await expect(composePage.panel).toBeVisible();
    await expect(composePage.subjectInput).toHaveValue(/Re:/);
  });

  test('should open forward with pre-filled subject', async ({ authenticatedPage }) => {
    // Expand an email first
    const firstCard = authenticatedPage.locator('.ib-card').first();
    await firstCard.click();
    const api = new MockApi();
    api.get(/\/email-accounts\/\d+\/emails\/\d+/, {
      ...mockEmailPage.content[0],
      bodyText: 'Hello world',
      bodyHtml: '<p>Hello world</p>',
      automationTraces: [],
    });
    await api.apply(authenticatedPage);
    await firstCard.click();
    await authenticatedPage.waitForTimeout(500);

    // Click forward
    await composePage.forwardBtn.click();
    await expect(composePage.panel).toBeVisible();
    await expect(composePage.subjectInput).toHaveValue(/Fwd:/);
  });

  test('should save draft', async ({ authenticatedPage }) => {
    await composePage.composeBtn.click();
    await composePage.fillTo('draft@example.com');
    await composePage.fillSubject('Draft Test');
    await composePage.saveDraftBtn.click();
    // Panel stays open after draft save
    await expect(composePage.panel).toBeVisible();
  });

  test('should discard and close panel', async () => {
    await composePage.composeBtn.click();
    await composePage.fillTo('discard@example.com');
    await composePage.fillSubject('To Discard');
    await composePage.discardBtn.click();
    await expect(composePage.panel).not.toBeVisible();
  });

  test('should disable send when only to is filled', async () => {
    await composePage.composeBtn.click();
    await composePage.fillTo('test@example.com');
    // Subject still empty
    await expect(composePage.sendBtn).toBeDisabled();
  });

  test('should disable send when only subject is filled', async () => {
    await composePage.composeBtn.click();
    await composePage.fillSubject('Test Subject');
    // To still empty
    await expect(composePage.sendBtn).toBeDisabled();
  });

  test('should reset fields when opening new compose after close', async () => {
    // Open and fill
    await composePage.composeBtn.click();
    await composePage.fillTo('first@example.com');
    await composePage.fillSubject('First Subject');
    await composePage.closeBtn.click();

    // Open again
    await composePage.composeBtn.click();
    await expect(composePage.toInput).toHaveValue('');
    await expect(composePage.subjectInput).toHaveValue('');
  });
});
