export const mockUsageResponse = {
  plan: {
    name: 'PRO',
    tokenLimit: 100000,
    automationLimit: 25,
    emailAccountLimit: 5,
    apiWebhookEnabled: false,
    costLimitCents: 500,
  },
  usage: {
    tokensUsedThisMonth: 31200,
    activeAutomations: 8,
    emailAccounts: 4,
    costUsedCents: 124,
    // 124.5 cents in micros — exercises the sub-cent precision path (124.5/500 = 24.9% → 25%).
    costUsedMicros: 1_245_000,
  },
  billingPeriod: {
    start: '2026-05-01T00:00:00Z',
    end: '2026-06-01T00:00:00Z',
  },
};

export const mockUsageResponseStarter = {
  plan: {
    name: 'STARTER',
    tokenLimit: 0,
    automationLimit: 3,
    emailAccountLimit: 2,
    apiWebhookEnabled: false,
    costLimitCents: 0,
  },
  usage: {
    tokensUsedThisMonth: 0,
    activeAutomations: 1,
    emailAccounts: 1,
    costUsedCents: 0,
    costUsedMicros: 0,
  },
  billingPeriod: {
    start: '2026-05-01T00:00:00Z',
    end: '2026-06-01T00:00:00Z',
  },
};

export const mockQuotaError429 = {
  status: 429,
  limitType: 'EMAIL_ACCOUNT',
  currentUsage: 5,
  maxAllowed: 5,
  planName: 'PRO',
  message: 'EMAIL_ACCOUNT quota exceeded: 5/5 (plan: PRO)',
  timestamp: '2026-05-17T12:00:00Z',
};
