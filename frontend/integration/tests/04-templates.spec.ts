import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

test.describe('Templates API', () => {
  let api: ApiClient;
  let templateId: string;
  let paramSetId: string;

  test.beforeAll(async ({ request }) => {
    api = new ApiClient(request);
    await api.register(uniqueEmail('tpl'), TEST_PASSWORD);
  });

  test('create parameter set', async () => {
    const res = await api.post('/api/v1/parameter-sets', {
      name: 'Customer Data',
      parameters: [
        { name: 'customerName', type: 'text', description: 'Customer full name', required: true, isList: false },
        { name: 'invoiceAmount', type: 'number', description: 'Total amount', required: false, isList: false },
      ],
    });
    expect(res.id).toBeTruthy();
    expect(res.name).toBe('Customer Data');
    expect(res.parameters.length).toBe(2);
    paramSetId = res.id;
  });

  test('create template with param references', async () => {
    const res = await api.post('/api/v1/templates', {
      name: 'Invoice Reply',
      subject: 'RE: Invoice for {{customerName}}',
      body: 'Dear {{customerName}},\n\nThank you for your invoice of {{invoiceAmount}}.\n\nBest regards',
      parameterSetId: paramSetId,
    });
    expect(res.id).toBeTruthy();
    expect(res.name).toBe('Invoice Reply');
    expect(res.params).toContain('customerName');
    expect(res.params).toContain('invoiceAmount');
    expect(res.parameterSetId).toBe(paramSetId);
    templateId = res.id;
  });

  test('list templates', async () => {
    const list = await api.get('/api/v1/templates');
    expect(list.length).toBeGreaterThanOrEqual(1);
    expect(list.find((t: any) => t.id === templateId)).toBeTruthy();
  });

  test('update template', async () => {
    const updated = await api.put(`/api/v1/templates/${templateId}`, {
      name: 'Updated Invoice Reply',
      subject: 'RE: Updated Invoice',
      body: 'Updated body text for {{customerName}}',
    });
    expect(updated.name).toBe('Updated Invoice Reply');
    expect(updated.params).toContain('customerName');
  });

  test('toggle lock', async () => {
    const locked = await api.patch(`/api/v1/templates/${templateId}/lock`);
    expect(locked.locked).toBe(true);

    const unlocked = await api.patch(`/api/v1/templates/${templateId}/lock`);
    expect(unlocked.locked).toBe(false);
  });

  test('delete template', async () => {
    await api.delete(`/api/v1/templates/${templateId}`);
  });

  test('delete parameter set', async () => {
    await api.delete(`/api/v1/parameter-sets/${paramSetId}`);
  });
});
