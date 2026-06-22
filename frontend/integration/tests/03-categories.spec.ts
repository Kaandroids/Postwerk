import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

test.describe('Categories API', () => {
  let api: ApiClient;
  let categoryId: string;

  test.beforeAll(async ({ request }) => {
    api = new ApiClient(request);
    await api.register(uniqueEmail('cat'), TEST_PASSWORD);
  });

  test('create category', async () => {
    const res = await api.post('/api/v1/categories', {
      name: 'Integration Invoices',
      color: '#10b981',
      description: 'Financial invoices received from vendors and suppliers for accounting purposes',
      positiveExample: 'Invoice #123 attached for order',
      negativeExample: 'Marketing newsletter from vendor',
    });
    expect(res.id).toBeTruthy();
    expect(res.name).toBe('Integration Invoices');
    expect(res.color).toBe('#10b981');
    expect(res.locked).toBe(false);
    categoryId = res.id;
  });

  test('list categories', async () => {
    const list = await api.get('/api/v1/categories');
    expect(list.length).toBeGreaterThanOrEqual(1);
    expect(list.find((c: any) => c.id === categoryId)).toBeTruthy();
  });

  test('get category by id', async () => {
    const cat = await api.get(`/api/v1/categories/${categoryId}`);
    expect(cat.name).toBe('Integration Invoices');
    expect(cat.positiveExample).toContain('Invoice');
  });

  test('update category', async () => {
    const updated = await api.put(`/api/v1/categories/${categoryId}`, {
      name: 'Updated Invoices',
      color: '#ef4444',
      description: 'Updated description for invoices that must be at least thirty chars long',
    });
    expect(updated.name).toBe('Updated Invoices');
    expect(updated.color).toBe('#ef4444');
  });

  test('toggle lock', async () => {
    const locked = await api.patch(`/api/v1/categories/${categoryId}/lock`);
    expect(locked.locked).toBe(true);

    const unlocked = await api.patch(`/api/v1/categories/${categoryId}/lock`);
    expect(unlocked.locked).toBe(false);
  });

  test('delete category', async () => {
    await api.delete(`/api/v1/categories/${categoryId}`);
    const list = await api.get('/api/v1/categories');
    expect(list.find((c: any) => c.id === categoryId)).toBeUndefined();
  });
});
