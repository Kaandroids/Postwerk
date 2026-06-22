export const mockEmailAccounts = [
  {
    id: 1,
    email: 'work@example.com',
    displayName: 'Work',
    color: '#6366f1',
    imapHost: 'imap.example.com',
    imapPort: 993,
    smtpHost: 'smtp.example.com',
    smtpPort: 587,
    readEnabled: true,
    writeEnabled: true,
    syncFromDate: '2024-01-01',
    isDefault: true,
    isActive: true,
    createdAt: '2024-01-15T10:00:00Z',
  },
  {
    id: 2,
    email: 'personal@example.com',
    displayName: 'Personal',
    color: '#f59e0b',
    imapHost: 'imap.personal.com',
    imapPort: 993,
    smtpHost: 'smtp.personal.com',
    smtpPort: 587,
    readEnabled: true,
    writeEnabled: false,
    syncFromDate: '2024-06-01',
    isDefault: false,
    isActive: true,
    createdAt: '2024-06-01T10:00:00Z',
  },
];

export const mockConnectionTestSuccess = {
  success: true,
  message: 'Verbindung erfolgreich',
};

export const mockConnectionTestFailure = {
  success: false,
  message: 'Verbindung fehlgeschlagen: Connection refused',
};
