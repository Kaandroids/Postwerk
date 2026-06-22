/** Mock data for the user-facing audit log (/dashboard/audit-log). */

export const mockAuditLogs = [
  {
    id: 'al-1', userId: 'u1', userName: 'Alice Admin', action: 'USER_LOGIN',
    detail: 'ip=1.2.3.4', ipAddress: '1.2.3.4', createdAt: '2026-06-21T10:00:00Z',
  },
  {
    id: 'al-2', userId: 'u1', userName: 'Alice Admin', action: 'AUTOMATION_ACTIVATED',
    detail: 'Automation: Invoice Router', ipAddress: '1.2.3.4', createdAt: '2026-06-21T09:30:00Z',
  },
  {
    id: 'al-3', userId: 'u2', userName: 'Bob Builder', action: 'EMAIL_SENT',
    detail: 'to=customer@acme.com', ipAddress: '5.6.7.8', createdAt: '2026-06-21T08:15:00Z',
  },
];

export const mockAuditLogPage = {
  content: mockAuditLogs,
  totalElements: 3,
  totalPages: 1,
  number: 0,
};

export const mockEmptyAuditLogPage = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
};
