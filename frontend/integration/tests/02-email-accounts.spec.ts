import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

test.describe('Email Accounts API', () => {
  let api: ApiClient;
  let accountId: string;

  const imapHost = process.env['TEST_IMAP_HOST'] || '';
  const imapPort = Number(process.env['TEST_IMAP_PORT'] || '993');
  const smtpHost = process.env['TEST_SMTP_HOST'] || '';
  const smtpPort = Number(process.env['TEST_SMTP_PORT'] || '587');
  const emailUser = process.env['TEST_EMAIL_USER'] || '';
  const emailPass = process.env['TEST_EMAIL_PASS'] || '';

  const hasRealEmail = !!(imapHost && emailUser && emailPass);

  test.beforeAll(async ({ request }) => {
    api = new ApiClient(request);
    await api.register(uniqueEmail('emailacc'), TEST_PASSWORD);
  });

  test('create email account', async () => {
    const data: any = {
      email: hasRealEmail ? emailUser : 'test-inbox@example.com',
      displayName: 'Integration Test Inbox',
      color: '#3b82f6',
      readEnabled: true,
      writeEnabled: true,
      isDefault: false,
    };

    if (hasRealEmail) {
      Object.assign(data, {
        imapHost, imapPort,
        imapUsername: emailUser,
        imapPassword: emailPass,
        imapSsl: true,
        smtpHost, smtpPort,
        smtpUsername: emailUser,
        smtpPassword: emailPass,
        smtpSsl: smtpPort === 465,
      });
    }

    const res = await api.post('/api/v1/email-accounts', data);
    expect(res.id).toBeTruthy();
    expect(res.email).toBe(data.email);
    expect(res.color).toBe('#3b82f6');
    expect(res.displayName).toBe('Integration Test Inbox');
    accountId = res.id;
  });

  test('list email accounts', async () => {
    const list = await api.get('/api/v1/email-accounts');
    expect(list.length).toBeGreaterThanOrEqual(1);
    const found = list.find((a: any) => a.id === accountId);
    expect(found).toBeTruthy();
  });

  test('update email account', async () => {
    const updated = await api.put(`/api/v1/email-accounts/${accountId}`, {
      email: hasRealEmail ? emailUser : 'test-inbox@example.com',
      displayName: 'Updated Inbox Name',
      color: '#ef4444',
      readEnabled: true,
      writeEnabled: false,
      isDefault: false,
    });
    expect(updated.displayName).toBe('Updated Inbox Name');
    expect(updated.color).toBe('#ef4444');
  });

  test('set as default account', async () => {
    const res = await api.patch(`/api/v1/email-accounts/${accountId}/default`);
    expect(res.isDefault).toBe(true);
  });

  if (hasRealEmail) {
    test('test IMAP connection', async () => {
      const res = await api.post('/api/v1/email-accounts/test-connection', {
        host: imapHost,
        port: imapPort,
        username: emailUser,
        password: emailPass,
        ssl: true,
        type: 'imap',
      }, 200);
      expect(res.success).toBe(true);
    });

    test('test SMTP connection', async () => {
      const res = await api.post('/api/v1/email-accounts/test-connection', {
        host: smtpHost,
        port: smtpPort,
        username: emailUser,
        password: emailPass,
        ssl: smtpPort === 465,
        type: 'smtp',
      }, 200);
      expect(res.success).toBe(true);
    });

    test('list IMAP folders', async () => {
      const folders = await api.get(`/api/v1/email-accounts/${accountId}/folders`);
      expect(Array.isArray(folders)).toBe(true);
      expect(folders.length).toBeGreaterThan(0);
      const names = folders.map((f: any) => f.name);
      expect(names.some((n: string) => /inbox/i.test(n))).toBe(true);
    });
  }

  test('delete email account', async () => {
    await api.delete(`/api/v1/email-accounts/${accountId}`);
    const list = await api.get('/api/v1/email-accounts');
    const found = list.find((a: any) => a.id === accountId);
    expect(found).toBeUndefined();
  });
});
