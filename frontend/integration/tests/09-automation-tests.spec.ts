import { test, expect } from '@playwright/test';
import { ApiClient, uniqueEmail, TEST_PASSWORD } from '../helpers/api-client';

test.describe('Automation Test Cases API', () => {
  let api: ApiClient;
  let automationId: string;
  let triggerNodeId: string;
  let filterNodeId: string;
  let testCaseId: string;

  test.beforeAll(async ({ request }) => {
    api = new ApiClient(request);
    await api.register(uniqueEmail('autotest'), TEST_PASSWORD);

    // Create automation
    const auto = await api.post('/api/v1/automations', {
      name: 'Test Automation',
      description: 'For test case testing',
      color: '#8b5cf6',
    });
    automationId = auto.id;

    // Build flow with trigger + filter
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
          label: 'Subject Filter',
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
    triggerNodeId = flow.nodes.find((n: any) => n.nodeType === 'EMAIL_TRIGGER').id;
    filterNodeId = flow.nodes.find((n: any) => n.nodeType === 'FILTER').id;
  });

  test('create test case', async () => {
    const res = await api.post(`/api/v1/automations/${automationId}/tests`, {
      name: 'Invoice Test',
      description: 'Tests that invoice emails are processed',
      emailInput: {
        from: 'billing@example.com',
        to: 'inbox@example.com',
        subject: 'Invoice #12345',
        body: 'Please find attached the invoice for your recent order.',
      },
      assertions: [
        {
          nodeId: triggerNodeId,
          expectedStatus: 'SUCCESS',
          field: null,
          expectedValue: null,
        },
      ],
    });
    expect(res.id).toBeTruthy();
    expect(res.name).toBe('Invoice Test');
    expect(res.emailInput.subject).toBe('Invoice #12345');
    testCaseId = res.id;
  });

  test('list test cases', async () => {
    const list = await api.get(`/api/v1/automations/${automationId}/tests`);
    expect(list.length).toBeGreaterThanOrEqual(1);
    expect(list.find((t: any) => t.id === testCaseId)).toBeTruthy();
  });

  test('run single test case (dry-run)', async () => {
    const result = await api.post(
      `/api/v1/automations/${automationId}/tests/${testCaseId}/run`,
      {},
      200,
    );
    expect(result.testCaseId).toBe(testCaseId);
    expect(result.status).toBeDefined();
    expect(['PASSED', 'FAILED', 'ERROR']).toContain(result.status);
    expect(result.nodeResults).toBeDefined();
    expect(result.durationMs).toBeGreaterThanOrEqual(0);
  });

  test('run all tests', async () => {
    const result = await api.post(
      `/api/v1/automations/${automationId}/tests/run-all`,
      {},
      200,
    );
    expect(result.totalTests).toBeGreaterThanOrEqual(1);
    expect(result.results).toBeDefined();
    expect(result.results.length).toBe(result.totalTests);
  });

  test('update test case', async () => {
    const updated = await api.put(
      `/api/v1/automations/${automationId}/tests/${testCaseId}`,
      {
        name: 'Updated Invoice Test',
        description: 'Updated description',
        emailInput: {
          from: 'updated@example.com',
          to: 'inbox@example.com',
          subject: 'Updated Invoice',
          body: 'Updated body',
        },
        assertions: [],
      },
    );
    expect(updated.name).toBe('Updated Invoice Test');
  });

  test('delete test case', async () => {
    await api.delete(`/api/v1/automations/${automationId}/tests/${testCaseId}`);
  });

  test.afterAll(async () => {
    if (automationId) {
      await api.delete(`/api/v1/automations/${automationId}`);
    }
  });
});
