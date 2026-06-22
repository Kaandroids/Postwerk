/** Mock data for the supervised-mode approval inbox (#3a, /dashboard/approvals). */

export const mockPendingActions = [
  {
    id: 'pa-1',
    automationId: 'auto-1',
    emailId: 'em-1',
    nodeId: 'node-1',
    nodeType: 'EMAIL_ACTION',
    nodeLabel: 'Forward to billing',
    actionDetail: { toAddress: 'billing@acme.com', subject: 'Invoice received' },
    status: 'PENDING',
    createdAt: '2026-06-21T10:00:00Z',
    decidedAt: null,
    decisionNote: null,
    triggerCategory: { id: 'cat-1', name: 'Invoices', confidence: 92 },
  },
  {
    id: 'pa-2',
    automationId: 'auto-1',
    emailId: 'em-2',
    nodeId: 'node-2',
    nodeType: 'SEND_EMAIL',
    nodeLabel: 'Auto-reply',
    actionDetail: { toAddress: 'lead@acme.com' },
    status: 'PENDING',
    createdAt: '2026-06-21T09:00:00Z',
    decidedAt: null,
    decisionNote: null,
    triggerCategory: null,
  },
];

export const mockPendingActionPage = {
  content: mockPendingActions,
  totalElements: 2,
  totalPages: 1,
  number: 0,
};

export const mockEmptyPendingActionPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
};
