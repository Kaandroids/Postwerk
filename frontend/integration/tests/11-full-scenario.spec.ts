import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

/**
 * Full end-to-end scenario:
 * Register → Email Account → Category → Template → Filter → Automation → Test → Usage → Cleanup
 */
test.describe('Full Integration Scenario', () => {
  let api: ApiClient;

  // Resource IDs for cleanup
  let accountId: string;
  let categoryId: string;
  let paramSetId: string;
  let templateId: string;
  let filterId: string;
  let automationId: string;

  const hasRealEmail = !!(
    process.env['TEST_IMAP_HOST'] &&
    process.env['TEST_EMAIL_USER'] &&
    process.env['TEST_EMAIL_PASS']
  );

  test.beforeAll(async ({ request }) => {
    api = new ApiClient(request);
  });

  // ─── 1. Register ────────────────────────────────────────────────

  test('1. Register new user', async () => {
    const res = await api.register(uniqueEmail('scenario'), TEST_PASSWORD);
    expect(res.accessToken).toBeTruthy();
    expect(res.role).toBe('USER');
  });

  // ─── 2. Email Account ──────────────────────────────────────────

  test('2. Create email account', async () => {
    const data: any = {
      email: hasRealEmail ? process.env['TEST_EMAIL_USER'] : 'scenario@example.com',
      displayName: 'Scenario Inbox',
      color: '#3b82f6',
      readEnabled: true,
      writeEnabled: true,
      isDefault: true,
    };

    if (hasRealEmail) {
      Object.assign(data, {
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
      });
    }

    const res = await api.post('/api/v1/email-accounts', data);
    expect(res.id).toBeTruthy();
    accountId = res.id;
  });

  // ─── 3. Sync Emails (if real IMAP) ─────────────────────────────

  test('3. Sync emails from IMAP', async () => {
    test.skip(!hasRealEmail, 'No real email — skip sync');

    const result = await api.post(
      `/api/v1/email-accounts/${accountId}/emails/sync`,
      {},
      200,
    );
    expect(typeof result.newEmails).toBe('number');
  });

  // ─── 4. Category ───────────────────────────────────────────────

  test('4. Create category', async () => {
    const res = await api.post('/api/v1/categories', {
      name: 'Scenario Invoices',
      color: '#10b981',
      description: 'Invoice emails for full integration scenario testing purposes',
      positiveExample: 'Invoice #123 for order',
      negativeExample: 'Weekly marketing digest',
    });
    expect(res.id).toBeTruthy();
    categoryId = res.id;
  });

  // ─── 5. Parameter Set ──────────────────────────────────────────

  test('5. Create parameter set', async () => {
    const res = await api.post('/api/v1/parameter-sets', {
      name: 'Invoice Data',
      parameters: [
        { name: 'customerName', type: 'text', description: 'Name', required: true, isList: false },
        { name: 'amount', type: 'number', description: 'Amount', required: true, isList: false },
      ],
    });
    expect(res.id).toBeTruthy();
    paramSetId = res.id;
  });

  // ─── 6. Template ───────────────────────────────────────────────

  test('6. Create template', async () => {
    const res = await api.post('/api/v1/templates', {
      name: 'Invoice Auto-Reply',
      subject: 'RE: Invoice from {{customerName}}',
      body: 'Dear {{customerName}},\n\nWe received your invoice for {{amount}}.\n\nThank you.',
      parameterSetId,
    });
    expect(res.id).toBeTruthy();
    expect(res.params).toContain('customerName');
    expect(res.params).toContain('amount');
    templateId = res.id;
  });

  // ─── 7. Filter ─────────────────────────────────────────────────

  test('7. Create email filter', async () => {
    const res = await api.post('/api/v1/email-filters', {
      name: 'Invoice Subject Filter',
      color: '#f59e0b',
      description: 'Matches invoices',
      groups: [
        {
          conditions: [
            { field: 'subject', operator: 'contains', value: 'invoice' },
          ],
        },
      ],
    });
    expect(res.id).toBeTruthy();
    filterId = res.id;
  });

  // ─── 8. Automation ─────────────────────────────────────────────

  test('8. Create automation with flow', async () => {
    // Create
    const auto = await api.post('/api/v1/automations', {
      name: 'Invoice Processor',
      description: 'Full scenario automation',
      color: '#8b5cf6',
    });
    automationId = auto.id;

    // Update flow
    const flow = await api.put(`/api/v1/automations/${automationId}/flow`, {
      nodes: [
        {
          id: 'trigger-1',
          nodeType: 'EMAIL_TRIGGER',
          label: 'New Email',
          positionX: 100,
          positionY: 200,
          config: JSON.stringify({ folder: 'INBOX' }),
        },
        {
          id: 'filter-1',
          nodeType: 'FILTER',
          label: 'Invoice Filter',
          positionX: 400,
          positionY: 200,
          config: JSON.stringify({ filterId }),
        },
        {
          id: 'delay-1',
          nodeType: 'DELAY',
          label: 'Wait 5 min',
          positionX: 700,
          positionY: 200,
          config: JSON.stringify({ delayMinutes: 5 }),
        },
      ],
      edges: [
        {
          id: 'edge-1',
          sourceNodeId: 'trigger-1',
          sourceHandle: 'new-email',
          targetNodeId: 'filter-1',
          targetHandle: 'input',
        },
        {
          id: 'edge-2',
          sourceNodeId: 'filter-1',
          sourceHandle: 'match',
          targetNodeId: 'delay-1',
          targetHandle: 'input',
        },
      ],
      viewport: JSON.stringify({ x: 0, y: 0, zoom: 1 }),
    });
    expect(flow.nodes.length).toBe(3);
    expect(flow.edges.length).toBe(2);
  });

  // ─── 9. Automation Test Case ───────────────────────────────────

  test('9. Create and run automation test', async () => {
    const detail = await api.get(`/api/v1/automations/${automationId}`);
    const triggerNode = detail.nodes.find(
      (n: any) => n.nodeType === 'EMAIL_TRIGGER',
    );

    // Create test case
    const tc = await api.post(`/api/v1/automations/${automationId}/tests`, {
      name: 'Scenario Test',
      description: 'Full scenario test case',
      emailInput: {
        from: 'vendor@billing.com',
        to: 'inbox@company.com',
        subject: 'Invoice #9999 for March',
        body: 'Please find attached the invoice.',
      },
      assertions: [
        {
          nodeId: triggerNode.id,
          expectedStatus: 'SUCCESS',
          field: null,
          expectedValue: null,
        },
      ],
    });
    expect(tc.id).toBeTruthy();

    // Run test
    const result = await api.post(
      `/api/v1/automations/${automationId}/tests/${tc.id}/run`,
      {},
      200,
    );
    expect(result.status).toBeDefined();
    expect(result.nodeResults.length).toBeGreaterThan(0);
    expect(result.durationMs).toBeGreaterThanOrEqual(0);
  });

  // ─── 10. Usage Endpoint ────────────────────────────────────────

  test('10. Check user usage', async () => {
    const usage = await api.get('/api/v1/users/me/usage');
    expect(usage.plan).toBeDefined();
    expect(usage.usage).toBeDefined();
  });

  // ─── 11. Cleanup ───────────────────────────────────────────────

  test('11. Cleanup all resources', async () => {
    if (automationId) await api.delete(`/api/v1/automations/${automationId}`);
    if (filterId) await api.delete(`/api/v1/email-filters/${filterId}`);
    if (templateId) await api.delete(`/api/v1/templates/${templateId}`);
    if (paramSetId) await api.delete(`/api/v1/parameter-sets/${paramSetId}`);
    if (categoryId) await api.delete(`/api/v1/categories/${categoryId}`);
    if (accountId) await api.delete(`/api/v1/email-accounts/${accountId}`);

    // Verify everything is cleaned up
    const accounts = await api.get('/api/v1/email-accounts');
    expect(accounts.find((a: any) => a.id === accountId)).toBeUndefined();
    const cats = await api.get('/api/v1/categories');
    expect(cats.find((c: any) => c.id === categoryId)).toBeUndefined();
  });
});
