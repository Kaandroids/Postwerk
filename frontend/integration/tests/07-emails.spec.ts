import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

const hasRealEmail = !!(
  process.env['TEST_IMAP_HOST'] &&
  process.env['TEST_EMAIL_USER'] &&
  process.env['TEST_EMAIL_PASS']
);

test.describe('Emails API (real IMAP)', () => {
  test.skip(!hasRealEmail, 'Skipped — no real email credentials in env');

  let api: ApiClient;
  let accountId: string;

  test.beforeAll(async ({ request }) => {
    api = new ApiClient(request);
    await api.register(uniqueEmail('emails'), TEST_PASSWORD);

    // Create email account with real credentials
    const account = await api.post('/api/v1/email-accounts', {
      email: process.env['TEST_EMAIL_USER'],
      displayName: 'Email Test Account',
      color: '#6366f1',
      readEnabled: true,
      writeEnabled: true,
      imapHost: process.env['TEST_IMAP_HOST'],
      imapPort: Number(process.env['TEST_IMAP_PORT'] || '993'),
      imapUsername: process.env['TEST_EMAIL_USER'],
      imapPassword: process.env['TEST_EMAIL_PASS'],
      imapSsl: true,
      smtpHost: process.env['TEST_SMTP_HOST'],
      smtpPort: Number(process.env['TEST_SMTP_PORT'] || '587'),
      smtpUsername: process.env['TEST_EMAIL_USER'],
      smtpPassword: process.env['TEST_EMAIL_PASS'],
      smtpSsl: Number(process.env['TEST_SMTP_PORT'] || '587') === 465,
      isDefault: true,
    });
    accountId = account.id;
  });

  test('sync emails from IMAP', async () => {
    const result = await api.post(
      `/api/v1/email-accounts/${accountId}/emails/sync`,
      {},
      200,
    );
    expect(result).toBeTruthy();
    expect(typeof result.newEmails).toBe('number');
  });

  test('list emails (paginated)', async () => {
    const page = await api.get(
      `/api/v1/email-accounts/${accountId}/emails?page=0&size=10`,
    );
    expect(page.content).toBeDefined();
    expect(Array.isArray(page.content)).toBe(true);
    expect(page.totalElements).toBeGreaterThanOrEqual(0);
  });

  test('get email detail', async () => {
    // First get a list to find an email
    const page = await api.get(
      `/api/v1/email-accounts/${accountId}/emails?page=0&size=1`,
    );
    test.skip(page.content.length === 0, 'No emails in inbox');

    const emailId = page.content[0].id;
    const detail = await api.get(
      `/api/v1/email-accounts/${accountId}/emails/${emailId}`,
    );
    expect(detail.id).toBe(emailId);
    expect(detail.subject).toBeTruthy();
    expect(detail.body).toBeDefined();
  });

  test('toggle read status', async () => {
    const page = await api.get(
      `/api/v1/email-accounts/${accountId}/emails?page=0&size=1`,
    );
    test.skip(page.content.length === 0, 'No emails');

    const emailId = page.content[0].id;
    const res = await api.patch(
      `/api/v1/email-accounts/${accountId}/emails/${emailId}/read`,
      { read: true },
    );
    expect(res.read).toBe(true);
  });

  test('toggle star', async () => {
    const page = await api.get(
      `/api/v1/email-accounts/${accountId}/emails?page=0&size=1`,
    );
    test.skip(page.content.length === 0, 'No emails');

    const emailId = page.content[0].id;
    const res = await api.patch(
      `/api/v1/email-accounts/${accountId}/emails/${emailId}/star`,
      { starred: true },
    );
    expect(res.starred).toBe(true);
  });

  test.afterAll(async () => {
    if (accountId) {
      await api.delete(`/api/v1/email-accounts/${accountId}`);
    }
  });
});
