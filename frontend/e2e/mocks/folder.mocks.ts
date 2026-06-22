export const mockFolders = [
  { id: 1, name: 'INBOX', role: 'INBOX', messageCount: 250, unreadCount: 12, lastSyncedAt: '2024-06-15T15:00:00Z' },
  { id: 2, name: 'Sent', role: 'SENT', messageCount: 180, unreadCount: 0, lastSyncedAt: '2024-06-15T15:00:00Z' },
  { id: 3, name: 'Drafts', role: 'DRAFTS', messageCount: 5, unreadCount: 0, lastSyncedAt: '2024-06-15T15:00:00Z' },
  { id: 4, name: 'Spam', role: 'SPAM', messageCount: 30, unreadCount: 8, lastSyncedAt: '2024-06-15T15:00:00Z' },
  { id: 5, name: 'Trash', role: 'TRASH', messageCount: 15, unreadCount: 0, lastSyncedAt: '2024-06-15T15:00:00Z' },
];

export const mockCustomFolder = {
  id: '6a1b2c3d-0000-0000-0000-000000000001',
  name: 'Rechnungen',
  role: 'OTHER',
  messageCount: 0,
  unreadCount: 0,
  lastSyncedAt: null,
};

export const mockFoldersWithCustom = [
  ...mockFolders,
  mockCustomFolder,
  { id: '6a1b2c3d-0000-0000-0000-000000000002', name: 'Projekte', role: 'OTHER', messageCount: 14, unreadCount: 3, lastSyncedAt: '2024-06-15T15:00:00Z' },
];

export const mockFolderCreated = {
  id: '6a1b2c3d-0000-0000-0000-000000000003',
  name: 'Neuer Ordner',
  role: 'OTHER',
  messageCount: 0,
  unreadCount: 0,
  lastSyncedAt: null,
};
