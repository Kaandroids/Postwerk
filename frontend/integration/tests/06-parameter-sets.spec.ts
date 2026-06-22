import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

test.describe('Parameter Sets API', () => {
  let api: ApiClient;
  let paramSetId: string;

  test.beforeAll(async ({ request }) => {
    api = new ApiClient(request);
    await api.register(uniqueEmail('pset'), TEST_PASSWORD);
  });

  test('create parameter set', async () => {
    const res = await api.post('/api/v1/parameter-sets', {
      name: 'Order Data',
      parameters: [
        { name: 'orderId', type: 'text', description: 'Order identifier', required: true, isList: false },
        { name: 'amount', type: 'number', description: 'Order total', required: true, isList: false },
        { name: 'items', type: 'text', description: 'Line items', required: false, isList: true },
      ],
    });
    expect(res.id).toBeTruthy();
    expect(res.name).toBe('Order Data');
    expect(res.parameters.length).toBe(3);
    paramSetId = res.id;
  });

  test('list parameter sets', async () => {
    const list = await api.get('/api/v1/parameter-sets');
    expect(list.length).toBeGreaterThanOrEqual(1);
    expect(list.find((p: any) => p.id === paramSetId)).toBeTruthy();
  });

  test('update parameter set', async () => {
    const updated = await api.put(`/api/v1/parameter-sets/${paramSetId}`, {
      name: 'Updated Order Data',
      parameters: [
        { name: 'orderId', type: 'text', description: 'Updated desc', required: true, isList: false },
      ],
    });
    expect(updated.name).toBe('Updated Order Data');
    expect(updated.parameters.length).toBe(1);
  });

  test('reject reserved name', async ({ request }) => {
    const client = new ApiClient(request);
    // reuse token from api
    const loginRes = await client.raw('POST', '/api/v1/auth/register', {
      fullName: 'Reserved Test',
      email: uniqueEmail('reserved'),
      password: 'SecureP@ss123!',
      termsAccepted: true,
      marketingOptIn: false,
    });
    const body = await loginRes.json();
    client.setToken(body.accessToken);

    const res = await client.raw('POST', '/api/v1/parameter-sets', {
      name: 'email',
      parameters: [],
    });
    expect([400, 409]).toContain(res.status());
  });

  test('toggle lock', async () => {
    const locked = await api.patch(`/api/v1/parameter-sets/${paramSetId}/lock`);
    expect(locked.locked).toBe(true);

    const unlocked = await api.patch(`/api/v1/parameter-sets/${paramSetId}/lock`);
    expect(unlocked.locked).toBe(false);
  });

  test('delete parameter set', async () => {
    await api.delete(`/api/v1/parameter-sets/${paramSetId}`);
  });
});
