export const mockAutomations = [
  {
    id: 1,
    name: 'Bestellungen verarbeiten',
    description: 'Automatische Verarbeitung eingehender Bestellungen',
    color: '#6366f1',
    status: 'ACTIVE',
    nodeCount: 4,
    edgeCount: 3,
    totalExecutions: 156,
    successCount: 148,
    failedCount: 8,
    lastRunAt: '2024-06-15T14:30:00Z',
    locked: false,
    createdAt: '2024-03-01T10:00:00Z',
    updatedAt: '2024-06-15T14:30:00Z',
    testModeStats: null,
  },
];

export const mockAutomationDetail = {
  ...mockAutomations[0],
  nodes: [
    { id: 'trigger-1', type: 'TRIGGER', position: { x: 100, y: 200 }, config: {} },
    { id: 'filter-1', type: 'FILTER', position: { x: 300, y: 200 }, config: { checks: [{ label: 'Check 1', groups: [{ conditions: [{ field: 'email.from', operator: 'CONTAINS', value: 'test' }] }] }] } },
    {
      id: 'categorize-1',
      type: 'CATEGORIZE',
      position: { x: 500, y: 200 },
      config: { categoryIds: [1] },
    },
    {
      id: 'action-1',
      type: 'EMAIL_ACTION',
      position: { x: 700, y: 200 },
      config: { actionMode: 'FORWARD', toAddress: 'team@example.com' },
    },
    {
      id: 'delay-1',
      type: 'DELAY',
      position: { x: 900, y: 200 },
      config: { delayMinutes: 30 },
    },
    {
      id: 'label-1',
      type: 'LABEL',
      position: { x: 1100, y: 200 },
      config: { categoryId: 'cat-1' },
    },
    {
      id: 'webhook-1',
      type: 'WEBHOOK',
      position: { x: 1300, y: 200 },
      config: { url: 'https://hooks.slack.com/services/xxx', method: 'POST', authType: 'NONE', body: '{"text":"{{subject}}"}', timeout: 30, responseSchemas: [{ name: 'Erfolg', condition: '2xx', parameterSetId: 'ps-1' }] },
    },
  ],
  edges: [
    { id: 'e1', source: 'trigger-1', target: 'filter-1' },
    { id: 'e2', source: 'filter-1', target: 'categorize-1' },
    { id: 'e3', source: 'categorize-1', target: 'action-1' },
  ],
  constants: [],
};
