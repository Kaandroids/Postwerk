/** Mock data for the production activity feed (#3d, /dashboard/activity). */

export const mockActivityEntries = [
  {
    traceId: 'tr-1',
    automationId: 'auto-1',
    automationName: 'Invoice Router',
    automationColor: '#6366f1',
    emailSubject: 'Invoice #123',
    emailFrom: 'vendor@acme.com',
    status: 'SUCCESS',
    startedAt: '2026-06-21T08:00:00Z',
    completedAt: '2026-06-21T08:00:05Z',
    errorMessage: null,
    steps: [
      { nodeType: 'CATEGORIZE', nodeLabel: 'Classify', resultStatus: 'SUCCESS', summary: 'Matched Invoices' },
      { nodeType: 'EMAIL_ACTION', nodeLabel: 'Forward', resultStatus: 'SUCCESS', summary: 'Forwarded to billing' },
    ],
  },
  {
    traceId: 'tr-2',
    automationId: 'auto-2',
    automationName: 'Spam Filter',
    automationColor: null,
    emailSubject: 'You won a prize',
    emailFrom: 'spam@example.com',
    status: 'FAILED',
    startedAt: '2026-06-21T07:00:00Z',
    completedAt: null,
    errorMessage: 'SMTP timeout',
    steps: [
      { nodeType: 'WEBHOOK', nodeLabel: 'Notify Slack', resultStatus: 'FAILED', summary: 'HTTP 500' },
    ],
  },
];

export const mockActivityPage = {
  content: mockActivityEntries,
  totalElements: 2,
  totalPages: 1,
  number: 0,
};

export const mockEmptyActivityPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
};
