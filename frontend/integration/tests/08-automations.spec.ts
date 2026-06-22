import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

test.describe('Automations API', () => {
  let api: ApiClient;
  let automationId: string;

  test.beforeAll(async ({ request }) => {
    api = new ApiClient(request);
    await api.register(uniqueEmail('auto'), TEST_PASSWORD);
  });

  test('create automation', async () => {
    const res = await api.post('/api/v1/automations', {
      name: 'Integration Test Automation',
      description: 'Created by integration tests',
      color: '#3b82f6',
    });
    expect(res.id).toBeTruthy();
    expect(res.name).toBe('Integration Test Automation');
    expect(res.status).toBeDefined();
    expect(res.nodeCount).toBe(0);
    automationId = res.id;
  });

  test('get automation detail', async () => {
    const detail = await api.get(`/api/v1/automations/${automationId}`);
    expect(detail.id).toBe(automationId);
    expect(detail.name).toBe('Integration Test Automation');
    expect(detail.nodes).toBeDefined();
    expect(detail.edges).toBeDefined();
  });

  test('update flow with nodes and edges', async () => {
    const res = await api.put(`/api/v1/automations/${automationId}/flow`, {
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
          label: 'Check Subject',
          positionX: 400,
          positionY: 200,
          config: JSON.stringify({ filterId: null }),
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
      ],
      viewport: JSON.stringify({ x: 0, y: 0, zoom: 1 }),
    });
    expect(res.nodes.length).toBe(2);
    expect(res.edges.length).toBe(1);
  });

  test('list automations', async () => {
    const list = await api.get('/api/v1/automations');
    expect(list.length).toBeGreaterThanOrEqual(1);
    const found = list.find((a: any) => a.id === automationId);
    expect(found).toBeTruthy();
    expect(found.nodeCount).toBe(2);
    expect(found.edgeCount).toBe(1);
  });

  test('update automation metadata', async () => {
    const updated = await api.put(`/api/v1/automations/${automationId}`, {
      name: 'Updated Automation',
      description: 'Updated description',
      color: '#ef4444',
    });
    expect(updated.name).toBe('Updated Automation');
  });

  test('toggle automation status', async () => {
    const activated = await api.patch(
      `/api/v1/automations/${automationId}/status`,
      { isActive: true },
    );
    expect(activated.status).toBeDefined();
  });

  test('toggle lock', async () => {
    const locked = await api.patch(`/api/v1/automations/${automationId}/lock`);
    expect(locked.locked).toBe(true);

    const unlocked = await api.patch(`/api/v1/automations/${automationId}/lock`);
    expect(unlocked.locked).toBe(false);
  });

  test('delete automation', async () => {
    await api.delete(`/api/v1/automations/${automationId}`);
    const list = await api.get('/api/v1/automations');
    expect(list.find((a: any) => a.id === automationId)).toBeUndefined();
  });
});
