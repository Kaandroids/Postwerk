/** Mock data for the topbar notification center (see doc/NOTIFICATION_SYSTEM_DESIGN.md). */

export const mockNotifications = [
  {
    id: 'n-1', category: 'APPROVAL', type: 'APPROVAL_PENDING', severity: 'ACTION_REQUIRED',
    titleKey: 'New approval needed', bodyKey: null, params: {}, linkUrl: '/dashboard/approvals',
    payload: {}, organizationId: null, read: false, createdAt: '2026-06-21T10:00:00Z',
  },
  {
    id: 'n-2', category: 'AUTOMATION', type: 'RUN_FAILED', severity: 'WARNING',
    titleKey: 'Automation failed', bodyKey: null, params: {}, linkUrl: null,
    payload: {}, organizationId: null, read: true, createdAt: '2026-06-21T09:00:00Z',
  },
];

export const mockNotificationList = { items: mockNotifications, unreadCount: 2, total: 2 };
export const mockUnreadCount = { count: 2 };
export const mockEmptyNotificationList = { items: [], unreadCount: 0, total: 0 };
export const mockZeroUnreadCount = { count: 0 };
