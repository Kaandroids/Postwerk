const makeEmail = (i: number) => ({
  id: i,
  messageId: `<msg-${i}@example.com>`,
  folder: 'INBOX',
  fromAddress: `sender${i}@example.com`,
  fromPersonal: `Sender ${i}`,
  toAddresses: ['work@example.com'],
  ccAddresses: [],
  subject: `Test Email Subject ${i}`,
  snippet: `This is the preview snippet for email number ${i}...`,
  receivedAt: new Date(2024, 5, 15, 10, i).toISOString(),
  isRead: i % 3 !== 0,
  isStarred: i % 5 === 0,
  hasAttachments: i % 4 === 0,
  sizeBytes: 1024 * (i + 1),
  categories: i % 2 === 0 ? [{ id: 1, name: 'Bestellung', color: '#6366f1' }] : [],
  automationTraceCount: i % 3 === 0 ? 1 : 0,
  processed: i % 3 === 0,
  approvalStatus: null,
});

const emails = Array.from({ length: 25 }, (_, i) => makeEmail(i + 1));

export const mockEmailPage = {
  content: emails.slice(0, 10),
  totalElements: 25,
  totalPages: 3,
  number: 0,
  size: 10,
  first: true,
  last: false,
};

export const mockEmailPageTwo = {
  content: emails.slice(10, 20),
  totalElements: 25,
  totalPages: 3,
  number: 1,
  size: 10,
  first: false,
  last: false,
};

export const mockEmailDetail = {
  ...emails[0],
  hasAttachments: true,
  bodyText: 'This is the plain text body of the email.',
  bodyHtml: '<p>This is the <strong>HTML body</strong> of the email.</p>',
  attachments: JSON.stringify([
    { name: 'document.pdf', size: 204800, contentType: 'application/pdf' },
    { name: 'image.png', size: 51200, contentType: 'image/png' },
  ]),
  automationTraces: [],
};

export const mockSyncResult = {
  newEmails: 5,
  updatedEmails: 2,
  folder: 'INBOX',
  syncedAt: new Date().toISOString(),
};
