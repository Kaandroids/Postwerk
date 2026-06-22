import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

test.describe('Filters API', () => {
  let api: ApiClient;
  let filterId: string;

  test.beforeAll(async ({ request }) => {
    api = new ApiClient(request);
    await api.register(uniqueEmail('filter'), TEST_PASSWORD);
  });

  test('create filter with DNF groups', async () => {
    const res = await api.post('/api/v1/email-filters', {
      name: 'Invoice Filter',
      color: '#f59e0b',
      description: 'Catches invoices from billing dept',
      groups: [
        {
          conditions: [
            { field: 'subject', operator: 'contains', value: 'invoice' },
            { field: 'from', operator: 'contains', value: '@billing.com' },
          ],
        },
        {
          conditions: [
            { field: 'subject', operator: 'contains', value: 'receipt' },
          ],
        },
      ],
    });
    expect(res.id).toBeTruthy();
    expect(res.name).toBe('Invoice Filter');
    expect(res.groups.length).toBe(2);
    expect(res.groups[0].conditions.length).toBe(2);
    expect(res.groups[1].conditions.length).toBe(1);
    filterId = res.id;
  });

  test('get filter detail', async () => {
    const f = await api.get(`/api/v1/email-filters/${filterId}`);
    expect(f.name).toBe('Invoice Filter');
    expect(f.color).toBe('#f59e0b');
    expect(f.groups[0].conditions[0].field).toBe('subject');
    expect(f.groups[0].conditions[0].operator).toBe('contains');
  });

  test('update filter — modify groups', async () => {
    const updated = await api.put(`/api/v1/email-filters/${filterId}`, {
      name: 'Updated Invoice Filter',
      color: '#8b5cf6',
      description: 'Updated filter description',
      groups: [
        {
          conditions: [
            { field: 'subject', operator: 'contains', value: 'payment' },
          ],
        },
      ],
    });
    expect(updated.name).toBe('Updated Invoice Filter');
    expect(updated.groups.length).toBe(1);
    expect(updated.groups[0].conditions[0].value).toBe('payment');
  });

  test('toggle lock', async () => {
    const locked = await api.patch(`/api/v1/email-filters/${filterId}/lock`);
    expect(locked.locked).toBe(true);

    const unlocked = await api.patch(`/api/v1/email-filters/${filterId}/lock`);
    expect(unlocked.locked).toBe(false);
  });

  test('delete filter', async () => {
    await api.delete(`/api/v1/email-filters/${filterId}`);
    const list = await api.get('/api/v1/email-filters');
    expect(list.find((f: any) => f.id === filterId)).toBeUndefined();
  });
});
