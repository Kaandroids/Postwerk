export const mockSecrets = [
  {
    id: 'sec-1',
    name: 'SLACK_TOKEN',
    description: 'Slack Bot API Token',
    version: 2,
    lastRotatedAt: '2026-05-10T08:00:00Z',
    createdAt: '2026-04-01T10:00:00Z',
    updatedAt: '2026-05-10T08:00:00Z',
  },
  {
    id: 'sec-2',
    name: 'GITHUB_PAT',
    description: null,
    version: 1,
    lastRotatedAt: null,
    createdAt: '2026-05-15T12:00:00Z',
    updatedAt: '2026-05-15T12:00:00Z',
  },
];

export const mockSecretCreated = {
  id: 'sec-3',
  name: 'NEW_SECRET',
  description: 'A new secret',
  version: 1,
  lastRotatedAt: null,
  createdAt: '2026-05-18T10:00:00Z',
  updatedAt: '2026-05-18T10:00:00Z',
};

export const mockSecretUpdated = {
  ...mockSecrets[0],
  version: 3,
  lastRotatedAt: '2026-05-18T10:00:00Z',
  updatedAt: '2026-05-18T10:00:00Z',
};

export const mockEmptySecrets: never[] = [];
