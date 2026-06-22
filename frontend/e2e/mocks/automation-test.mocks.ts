export const mockTestCases = [
  {
    id: '1',
    name: 'Bestellung von Amazon',
    description: 'Tests that Amazon order emails are categorized and forwarded',
    emailInput: {
      from: 'order@amazon.de',
      to: 'user@example.com',
      subject: 'Ihre Bestellung #12345',
      body: 'Vielen Dank für Ihre Bestellung. Ihre Bestellnummer lautet #12345.',
      receivedAt: null,
      inReplyTo: null,
      categoryIds: null,
    },
    assertions: [
      { nodeId: 'trigger-1', expectedStatus: 'PASSED', field: null, expectedValue: null },
      { nodeId: 'filter-1', expectedStatus: 'MATCHED', field: null, expectedValue: null },
    ],
    sortOrder: 0,
    lastResult: {
      status: 'PASSED',
      passedCount: 2,
      totalCount: 2,
      durationMs: 320,
      executedAt: '2024-06-15T14:00:00Z',
    },
    createdAt: '2024-05-01T10:00:00Z',
  },
  {
    id: '2',
    name: 'Newsletter ignorieren',
    description: null,
    emailInput: {
      from: 'newsletter@shop.de',
      to: 'user@example.com',
      subject: 'Unsere neuesten Angebote',
      body: 'Schauen Sie sich unsere neuesten Angebote an!',
      receivedAt: null,
      inReplyTo: null,
      categoryIds: null,
    },
    assertions: [
      { nodeId: 'filter-1', expectedStatus: 'NOT_MATCHED', field: null, expectedValue: null },
    ],
    sortOrder: 1,
    lastResult: null,
    createdAt: '2024-05-10T08:00:00Z',
  },
];

export const mockTestResult = {
  id: '100',
  testCaseId: '1',
  testCaseName: 'Bestellung von Amazon',
  status: 'PASSED',
  nodeResults: [
    { nodeId: 'trigger-1', nodeType: 'TRIGGER', nodeLabel: 'E-Mail Eingang', resultStatus: 'PASSED', resultDetail: {} },
    { nodeId: 'filter-1', nodeType: 'FILTER', nodeLabel: 'Bestellfilter', resultStatus: 'MATCHED', resultDetail: { matched: true } },
    { nodeId: 'extract-1', nodeType: 'EXTRACT', nodeLabel: 'Daten extrahieren', resultStatus: 'EXTRACTED', resultDetail: { extractedValues: { 'extract-out-0': { orderNumber: '#12345', orderDate: '2024-06-15' } } } },
    { nodeId: 'categorize-1', nodeType: 'CATEGORIZE', nodeLabel: 'Kategorisierung', resultStatus: 'CATEGORIZED', resultDetail: { categoryName: 'Bestellungen', categoryColor: '#3b82f6', confidence: 95, threshold: 70, accepted: true } },
    { nodeId: 'forward-1', nodeType: 'EMAIL_ACTION', nodeLabel: 'Weiterleiten', resultStatus: 'SIMULATED', resultDetail: { actionMode: 'FORWARD', toAddress: 'logistics@example.com', reason: 'dry-run' } },
    { nodeId: 'delay-1', nodeType: 'DELAY', nodeLabel: 'Verzögerung', resultStatus: 'SIMULATED', resultDetail: { delayMinutes: 30, delayedUntil: '2024-06-16T10:30:00Z' } },
    { nodeId: 'label-1', nodeType: 'LABEL', nodeLabel: 'Etikett', resultStatus: 'SIMULATED', resultDetail: { categoryName: 'Bestellungen', categoryColor: '#3b82f6' } },
    { nodeId: 'webhook-1', nodeType: 'WEBHOOK', nodeLabel: 'Slack Notification', resultStatus: 'SIMULATED', resultDetail: { method: 'POST', url: 'https://hooks.slack.com/services/xxx', reason: 'dry-run' } },
  ],
  assertionResults: [
    { assertionIndex: 0, passed: true, expected: 'PASSED', actual: 'PASSED' },
    { assertionIndex: 1, passed: true, expected: 'MATCHED', actual: 'MATCHED' },
  ],
  durationMs: 285,
  errorMessage: null,
  executedAt: '2024-06-16T10:00:00Z',
};

export const mockRunAllResponse = {
  totalTests: 2,
  passed: 2,
  failed: 0,
  errors: 0,
  results: [
    mockTestResult,
    {
      id: '101',
      testCaseId: '2',
      testCaseName: 'Newsletter ignorieren',
      status: 'PASSED',
      nodeResults: [
        { nodeId: 'trigger-1', nodeType: 'TRIGGER', nodeLabel: 'E-Mail Eingang', resultStatus: 'PASSED', resultDetail: {} },
        { nodeId: 'filter-1', nodeType: 'FILTER', nodeLabel: 'Bestellfilter', resultStatus: 'NOT_MATCHED', resultDetail: {} },
      ],
      assertionResults: [
        { assertionIndex: 0, passed: true, expected: 'NOT_MATCHED', actual: 'NOT_MATCHED' },
      ],
      durationMs: 125,
      errorMessage: null,
      executedAt: '2024-06-16T10:00:01Z',
    },
  ],
};
