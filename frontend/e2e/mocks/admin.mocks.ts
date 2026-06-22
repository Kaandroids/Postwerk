export const mockAdminStats = {
  totalUsers: 150,
  activeUsers: 142,
  deletedUsers: 8,
  newUsersLast7Days: 12,
  newUsersLast30Days: 45,
  totalPromptTokens: 1250000,
  totalOutputTokens: 830000,
  totalAutomationExecutions: 5420,
  successfulExecutions: 5100,
  failedExecutions: 320,
  activeAutomations: 78,
  totalEmails: 48200,
};

export const mockAdminUsers = {
  content: [
    {
      id: 'u1',
      email: 'admin@example.com',
      fullName: 'Admin User',
      company: 'Postwerk GmbH',
      role: 'ADMIN',
      staffRole: 'SUPER_ADMIN',
      lastLoginAt: '2026-05-14T10:00:00Z',
      lastLoginIp: '192.168.1.1',
      createdAt: '2024-01-15T10:00:00Z',
      deleted: false,
      emailAccountCount: 3,
      automationCount: 5,
      totalTokensUsed: 250000,
      planName: 'PRO',
      orgCount: 2,
      aiCostMicrosThisMonth: 3800000,
      costLimitCents: 500,
    },
    {
      id: 'u2',
      email: 'user@example.com',
      fullName: 'Regular User',
      company: 'Test Corp',
      role: 'USER',
      staffRole: null,
      lastLoginAt: '2026-05-13T08:30:00Z',
      lastLoginIp: '10.0.0.1',
      createdAt: '2024-06-20T14:00:00Z',
      deleted: false,
      emailAccountCount: 1,
      automationCount: 2,
      totalTokensUsed: 45000,
      planName: 'FREE',
      orgCount: 1,
      aiCostMicrosThisMonth: 0,
      costLimitCents: 0,
    },
    {
      id: 'u3',
      email: 'jane@example.com',
      fullName: 'Jane Doe',
      company: null,
      role: 'USER',
      staffRole: null,
      lastLoginAt: null,
      lastLoginIp: null,
      createdAt: '2025-03-10T09:00:00Z',
      deleted: false,
      emailAccountCount: 0,
      automationCount: 0,
      totalTokensUsed: 0,
      planName: 'ENTERPRISE',
      orgCount: 1,
      aiCostMicrosThisMonth: 12000000,
      costLimitCents: -1,
    },
  ],
  totalElements: 3,
  totalPages: 1,
  number: 0,
  size: 20,
};

export const mockAdminUserDetail = mockAdminUsers.content[0];

export const mockAiUsageStats = {
  totalPromptTokens: 1250000,
  totalOutputTokens: 830000,
  totalTokens: 2080000,
  totalBillableChars: 6500000,
  totalCostCents: 1245,
  byModel: [
    { model: 'claude-sonnet-4-6', promptTokens: 800000, outputTokens: 500000, totalTokens: 1300000 },
    { model: 'claude-haiku-4-5-20251001', promptTokens: 450000, outputTokens: 330000, totalTokens: 780000 },
  ],
  byOperation: [
    { operation: 'CLASSIFY', promptTokens: 600000, outputTokens: 300000, totalTokens: 900000 },
    { operation: 'EXTRACT', promptTokens: 400000, outputTokens: 350000, totalTokens: 750000 },
    { operation: 'SUMMARIZE', promptTokens: 250000, outputTokens: 180000, totalTokens: 430000 },
  ],
};

export const mockAiUsageByUser = [
  { userId: 'u1', email: 'admin@example.com', fullName: 'Admin User', promptTokens: 500000, outputTokens: 300000, totalTokens: 800000, requestCount: 420, costCents: 845 },
  { userId: 'u2', email: 'user@example.com', fullName: 'Regular User', promptTokens: 200000, outputTokens: 150000, totalTokens: 350000, requestCount: 180, costCents: 400 },
];

export const mockAiUsageTimeline = [
  { date: '2026-04-15', value: 45000 },
  { date: '2026-04-16', value: 52000 },
  { date: '2026-04-17', value: 38000 },
  { date: '2026-04-18', value: 61000 },
  { date: '2026-04-19', value: 49000 },
  { date: '2026-04-20', value: 55000 },
];

export const mockAutomationStats = {
  totalExecutions: 5420,
  successCount: 5100,
  failedCount: 320,
  runningCount: 0,
  successRate: 94.1,
  activeAutomations: 78,
  totalAutomations: 95,
  topAutomations: [
    { automationId: 'a1', automationName: 'Bestellungen verarbeiten', executionCount: 1200, successCount: 1150, failedCount: 50 },
    { automationId: 'a2', automationName: 'Newsletter sortieren', executionCount: 800, successCount: 790, failedCount: 10 },
    { automationId: 'a3', automationName: 'Support-Tickets', executionCount: 650, successCount: 600, failedCount: 50 },
  ],
};

export const mockAutomationExecutions = {
  content: [
    { id: 'e1', automationId: 'a1', automationName: 'Bestellungen verarbeiten', status: 'SUCCESS', processedCount: 15, errorLog: null, triggeredAt: '2026-05-14T10:00:00Z', completedAt: '2026-05-14T10:01:00Z' },
    { id: 'e2', automationId: 'a2', automationName: 'Newsletter sortieren', status: 'FAILED', processedCount: 3, errorLog: 'Connection timeout', triggeredAt: '2026-05-14T09:30:00Z', completedAt: '2026-05-14T09:30:30Z' },
    { id: 'e3', automationId: 'a1', automationName: 'Bestellungen verarbeiten', status: 'SUCCESS', processedCount: 8, errorLog: null, triggeredAt: '2026-05-14T08:00:00Z', completedAt: '2026-05-14T08:00:45Z' },
  ],
  totalElements: 3,
  totalPages: 1,
  number: 0,
  size: 20,
};

export const mockAdminAuditLogs = {
  content: [
    { id: 'log1', userId: 'u1', userEmail: 'admin@example.com', userName: 'Admin User', action: 'LOGIN', detail: null, ipAddress: '192.168.1.1', createdAt: '2026-05-14T10:00:00Z' },
    { id: 'log2', userId: 'u2', userEmail: 'user@example.com', userName: 'Regular User', action: 'EMAIL_SYNC', detail: 'Synced 15 emails', ipAddress: '10.0.0.1', createdAt: '2026-05-14T09:00:00Z' },
    { id: 'log3', userId: 'u1', userEmail: 'admin@example.com', userName: 'Admin User', action: 'ROLE_UPDATE', detail: 'Changed user role to ADMIN', ipAddress: '192.168.1.1', createdAt: '2026-05-13T14:00:00Z' },
  ],
  totalElements: 3,
  totalPages: 1,
  number: 0,
  size: 20,
};

export const mockAdminPlans = [
  { id: 'p1', name: 'FREE', tokenLimit: 10000, automationLimit: 3, emailAccountLimit: 2, price: 0, costLimitCents: 0, apiWebhookEnabled: false, inboundWebhookLimit: 0, marketplacePublishEnabled: false, isDefault: true, userCount: 120, createdAt: '2024-01-01T00:00:00Z' },
  { id: 'p2', name: 'PRO', tokenLimit: 500000, automationLimit: 50, emailAccountLimit: 20, price: 29, costLimitCents: 500, apiWebhookEnabled: false, inboundWebhookLimit: 3, marketplacePublishEnabled: true, isDefault: false, userCount: 25, createdAt: '2024-01-01T00:00:00Z' },
  { id: 'p3', name: 'ENTERPRISE', tokenLimit: 0, automationLimit: 0, emailAccountLimit: 0, price: 99, costLimitCents: -1, apiWebhookEnabled: true, inboundWebhookLimit: -1, marketplacePublishEnabled: true, isDefault: false, userCount: 5, createdAt: '2024-01-01T00:00:00Z' },
];

export const mockAdminPricing = [
  { id: 'mp1', model: 'gemini-2.5-flash', inputPerMillion: 0.15, outputPerMillion: 0.60, updatedAt: '2024-01-01T00:00:00Z' },
  { id: 'mp2', model: 'gemini-2.5-pro', inputPerMillion: 1.25, outputPerMillion: 10.00, updatedAt: '2024-01-01T00:00:00Z' },
  { id: 'mp3', model: 'gemini-embedding-001', inputPerMillion: 0.006, outputPerMillion: 0.0, updatedAt: '2024-01-01T00:00:00Z' },
];

// Staff identity (GET /admin/me) — drives the "is staff" guard + admin-mode toggle. SUPER_ADMIN = all caps.
export const mockStaffIdentity = {
  email: 'admin@example.com',
  role: 'ADMIN',
  staffRole: 'SUPER_ADMIN',
  permissions: [
    'PLATFORM_DASHBOARD_VIEW', 'USER_VIEW', 'USER_MANAGE', 'USER_CREDENTIAL_RESET',
    'ORG_VIEW', 'ORG_MANAGE', 'PLAN_VIEW', 'PLAN_MANAGE', 'BILLING_VIEW', 'BILLING_MANAGE', 'QUOTA_OVERRIDE',
    'AI_USAGE_VIEW', 'AUTOMATION_OVERSIGHT_VIEW', 'INFRA_VIEW', 'INFRA_MANAGE', 'MARKETPLACE_MODERATE',
    'COMPLIANCE_VIEW', 'COMPLIANCE_MANAGE', 'AUDIT_LOG_VIEW', 'FEATURE_FLAG_MANAGE', 'ANNOUNCEMENT_MANAGE',
    'STAFF_MANAGE', 'PROMPT_MANAGE',
  ],
};

export const mockAdminOrgs = {
  content: [
    { id: 'o1', name: 'Acme Team', slug: 'acme', personal: false, ownerUserId: 'u1', ownerEmail: 'admin@example.com', ownerName: 'Admin User', planName: 'PRO', memberCount: 5, createdAt: '2024-02-01T00:00:00Z', suspendedAt: null },
    { id: 'o2', name: 'Jane (personal)', slug: 'jane', personal: true, ownerUserId: 'u3', ownerEmail: 'jane@example.com', ownerName: 'Jane Doe', planName: 'FREE', memberCount: 1, createdAt: '2025-03-10T09:00:00Z', suspendedAt: null },
  ],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 20,
};

export const mockAdminOrgDetail = {
  ...mockAdminOrgs.content[0],
  planId: 'p2',
  mailboxCount: 4,
  automationCount: 12,
  aiCostMicrosThisMonth: 1850000,
  suspensionReason: null,
  members: [
    { userId: 'u1', email: 'admin@example.com', fullName: 'Admin User', role: 'OWNER', status: 'ACTIVE', joinedAt: '2024-02-01T00:00:00Z' },
    { userId: 'u2', email: 'user@example.com', fullName: 'Regular User', role: 'MEMBER', status: 'ACTIVE', joinedAt: '2024-03-01T00:00:00Z' },
  ],
};

// ── Wired detail-tab payloads (Users + Organizations) ───────────────────
export const mockAdminUserOrgs = [
  { orgId: 'o1', name: 'Acme Team', slug: 'acme', personal: false, role: 'OWNER', status: 'ACTIVE', joinedAt: '2024-02-01T00:00:00Z' },
  { orgId: 'o3', name: 'Admin (personal)', slug: 'admin', personal: true, role: 'OWNER', status: 'ACTIVE', joinedAt: '2024-01-15T10:00:00Z' },
];

export const mockAdminUserMailboxes = [
  { id: 'mb1', email: 'admin@acme.com', displayName: 'Acme Support', color: '#4f8cff', active: true, createdAt: '2024-02-02T00:00:00Z' },
  { id: 'mb2', email: 'sales@acme.com', displayName: 'Acme Sales', color: '#22c55e', active: false, createdAt: '2024-03-05T00:00:00Z' },
];

// ── Users support tooling (notes + sessions) ────────────────────────────
export const mockStaffNotes = [
  { id: 'n1', body: 'Verified identity over phone on 2026-06-10.', authorName: 'Admin User', authorEmail: 'admin@example.com', createdAt: '2026-06-10T12:00:00Z' },
  { id: 'n2', body: 'Requested data export; handled via GDPR flow.', authorName: 'Support Agent', authorEmail: 'support@example.com', createdAt: '2026-05-28T09:30:00Z' },
];

export const mockUserSessions = { activeSessions: 2 };

export const mockAdminOrgAutomations = [
  { id: 'a1', name: 'Process orders', status: 'ACTIVE', kind: 'AUTOMATION', createdAt: '2024-02-10T00:00:00Z', lastRunAt: '2026-06-15T08:00:00Z' },
  { id: 'a2', name: 'Lookup customer', status: 'PAUSED', kind: 'INTEGRATION', createdAt: '2024-04-01T00:00:00Z', lastRunAt: null },
];

export const mockAdminOrgMailboxes = [
  { id: 'mb1', email: 'admin@acme.com', displayName: 'Acme Support', color: '#4f8cff', active: true, createdAt: '2024-02-02T00:00:00Z' },
];

export const mockPlanSummaries = [
  { id: 'p1', name: 'FREE', tokenLimit: 10000, automationLimit: 3, emailAccountLimit: 2, price: 0, costLimitCents: 0 },
  { id: 'p2', name: 'PRO', tokenLimit: 500000, automationLimit: 50, emailAccountLimit: 20, price: 29, costLimitCents: 500 },
  { id: 'p3', name: 'ENTERPRISE', tokenLimit: 0, automationLimit: 0, emailAccountLimit: 0, price: 99, costLimitCents: -1 },
];

// ── Quota Overrides (GET /admin/quota-overrides + /kpis) ─────────────────
export const mockQuotaOverrides = {
  content: [
    {
      id: 'qov_3b71c4', targetType: 'USER', targetId: 'u2', targetName: 'Lena Wagner',
      targetEmailOrSlug: 'lena.wagner@nordlicht.de', basePlan: 'PRO', kind: 'CREDIT',
      amountCents: 5000, baseCapCents: 20000, effectiveCapCents: 25000, currentSpendCents: 19120,
      expiresAt: '2026-06-23T00:00:00Z', reason: 'Goodwill credit after a failed sync caused duplicate AI runs (ticket #4821).',
      grantedByName: 'Carla Neumann', createdAt: '2026-06-09T00:00:00Z', status: 'active',
    },
    {
      id: 'qov_8f2a91', targetType: 'ORG', targetId: 'o1', targetName: 'Postwerk GmbH',
      targetEmailOrSlug: 'postwerk', basePlan: 'BUSINESS', kind: 'CAP',
      amountCents: null, baseCapCents: 50000, effectiveCapCents: 200000, currentSpendCents: 163400,
      expiresAt: '2026-06-30T00:00:00Z', reason: 'Q2 outbound campaign — temporary cap raise approved by account manager.',
      grantedByName: 'Marius Kessler', createdAt: '2026-06-02T00:00:00Z', status: 'active',
    },
    {
      id: 'qov_d04e22', targetType: 'ORG', targetId: 'o4', targetName: 'Aurora Logistik',
      targetEmailOrSlug: 'aurora-logistik', basePlan: 'ENTERPRISE', kind: 'UNLIMITED',
      amountCents: null, baseCapCents: 200000, effectiveCapCents: null, currentSpendCents: 612300,
      expiresAt: null, reason: 'Contracted uncapped AI usage for peak logistics season (signed addendum A-2026-14).',
      grantedByName: 'Marius Kessler', createdAt: '2026-05-28T00:00:00Z', status: 'active',
    },
  ],
  totalElements: 3,
  totalPages: 1,
  number: 0,
  size: 10,
};

export const mockQuotaKpis = {
  activeCount: 3,
  creditGrantedThisMonthCents: 5000,
  over80Count: 2,
  expiringIn7Count: 1,
};

// ── Email Health (GET /admin/email-health/*) ─────────────────────────────
export const mockEmailHealthMailboxes = {
  content: [
    {
      id: 'mbx_4a01aa00-0000-0000-0000-000000000001', email: 'support@nordlicht.de', displayName: 'Nordlicht Support',
      color: '#4f8cff', ownerOrgId: 'o1', ownerOrgName: 'Postwerk GmbH', ownerEmail: 'lena.wagner@nordlicht.de',
      protocols: ['IMAP', 'SMTP'], health: 'failing', paused: false, lastSyncAt: '2026-06-16T02:00:00Z',
      syncAgoMinutes: 3120, stale: true, lastError: 'Connection timed out (mx-eu-3)', server: 'mx-eu-3',
      queueDepth: null, imapConfigured: true, smtpConfigured: true,
    },
    {
      id: 'mbx_4a01aa00-0000-0000-0000-000000000002', email: 'sales@aurora-logistik.de', displayName: 'Aurora Sales',
      color: '#22c55e', ownerOrgId: 'o4', ownerOrgName: 'Aurora Logistik', ownerEmail: 'mk@aurora-logistik.de',
      protocols: ['IMAP'], health: 'auth_error', paused: false, lastSyncAt: '2026-06-17T22:00:00Z',
      syncAgoMinutes: 480, stale: false, lastError: 'AUTHENTICATE failed: invalid credentials', server: 'mx-eu-1',
      queueDepth: null, imapConfigured: true, smtpConfigured: false,
    },
    {
      id: 'mbx_4a01aa00-0000-0000-0000-000000000003', email: 'hello@postwerk.de', displayName: 'Postwerk',
      color: '#a855f7', ownerOrgId: 'o1', ownerOrgName: 'Postwerk GmbH', ownerEmail: 'admin@postwerk.de',
      protocols: ['IMAP', 'SMTP'], health: 'ok', paused: false, lastSyncAt: '2026-06-18T04:50:00Z',
      syncAgoMinutes: 18, stale: false, lastError: null, server: 'mx-eu-1',
      queueDepth: null, imapConfigured: true, smtpConfigured: true,
    },
    {
      id: 'mbx_4a01aa00-0000-0000-0000-000000000004', email: 'paused@example.com', displayName: 'Paused Box',
      color: '#f59e0b', ownerOrgId: 'o4', ownerOrgName: 'Aurora Logistik', ownerEmail: 'ops@aurora-logistik.de',
      protocols: ['IMAP'], health: 'ok', paused: true, lastSyncAt: '2026-06-15T10:00:00Z',
      syncAgoMinutes: 4090, stale: true, lastError: null, server: 'mx-eu-2',
      queueDepth: null, imapConfigured: true, smtpConfigured: false,
    },
  ],
  totalElements: 4,
  totalPages: 1,
  number: 0,
  size: 10,
};

export const mockEmailHealthKpis = {
  total: 28, healthy: 22, failing: 3, authErrors: 3, paused: 1, avgSyncLagMinutes: 7,
};

export const mockEmailHealthClusters = [
  { host: 'mx-eu-1', healthy: 9, total: 10, failing: 0, bad: 1, status: 'warn' },
  { host: 'mx-eu-2', healthy: 7, total: 7, failing: 0, bad: 0, status: 'ok' },
  { host: 'mx-eu-3', healthy: 3, total: 6, failing: 2, bad: 3, status: 'down' },
  { host: 'mx-eu-4', healthy: 3, total: 5, failing: 1, bad: 1, status: 'warn' },
];

export const mockEmailHealthDetail = {
  ...mockEmailHealthMailboxes.content[0],
  imapHost: 'imap.mx-eu-3.example.com', imapPort: 993, imapSsl: true, readEnabled: true,
  smtpHost: 'smtp.mx-eu-3.example.com', smtpPort: 587, smtpSsl: true, sendEnabled: true,
  lastErrorAt: '2026-06-16T02:00:00Z', createdAt: '2024-02-02T00:00:00Z',
  recentAttempts: [
    { at: '2026-06-16T02:00:00Z', ok: false, message: 'Connection timed out (mx-eu-3)' },
  ],
};

// ── System Health (GET /admin/system-health/*) ───────────────────────────
export const mockSystemHealthSubsystems = [
  { id: 'api', name: 'API gateway', kind: 'API', version: null, status: 'ok', primary: '142ms avg · 0.2% err',
    metrics: { 'Latency (avg)': '142 ms', 'Error rate (5xx)': '0.2 %', 'Requests (total)': 48210, 'Uptime': '6d 4h', 'Instances': 1 },
    lastCheckedMinutes: 0, lastError: null, recentChecks: [{ at: '2026-06-18T06:30:00Z', ok: true, message: 'Probe OK' }] },
  { id: 'postgres', name: 'PostgreSQL', kind: 'Database', version: 'PostgreSQL 17.2', status: 'ok', primary: 'pool 18/40',
    metrics: { 'Pool active': 18, 'Pool idle': 5, 'Pool total': 23, 'Pool max': 40 },
    lastCheckedMinutes: 0, lastError: null, recentChecks: [{ at: '2026-06-18T06:30:00Z', ok: true, message: 'Probe OK' }] },
  { id: 'redis', name: 'Redis cache', kind: 'Cache', version: 'Redis 7.2', status: 'down', primary: 'unreachable',
    metrics: {}, lastCheckedMinutes: 0, lastError: 'Redis replica unreachable — failover active',
    recentChecks: [{ at: '2026-06-18T06:30:00Z', ok: false, message: 'Redis replica unreachable — failover active' }] },
  { id: 'scheduler', name: 'Job scheduler', kind: 'Scheduler', version: null, status: 'ok', primary: 'queue 47',
    metrics: { 'Pending approvals': 12, 'Delayed emails': 35, 'Queue depth': 47 },
    lastCheckedMinutes: 0, lastError: null, recentChecks: [{ at: '2026-06-18T06:30:00Z', ok: true, message: 'Probe OK' }] },
  { id: 'email-sync', name: 'Email-sync workers', kind: 'Workers', version: null, status: 'degraded', primary: '28 mailboxes · 3 failing',
    metrics: { 'Read-enabled mailboxes': 28, 'Failing': 3, 'Worker pool (max)': 4 },
    lastCheckedMinutes: 0, lastError: '3 mailbox(es) failing to sync', recentChecks: [{ at: '2026-06-18T06:30:00Z', ok: false, message: '3 mailbox(es) failing to sync' }] },
  { id: 'smtp', name: 'SMTP send pipeline', kind: 'Email', version: null, status: 'ok', primary: '24 senders · synchronous',
    metrics: { 'Send-enabled mailboxes': 24, 'Mode': 'synchronous (no queue)', 'Queue depth': 'n/a' },
    lastCheckedMinutes: 0, lastError: null, recentChecks: [{ at: '2026-06-18T06:30:00Z', ok: true, message: 'Probe OK' }] },
  { id: 'gemini', name: 'AI provider · Gemini', kind: 'External', version: 'gemini-2.5-flash', status: 'ok', primary: 'gemini-2.5-flash · closed',
    metrics: { 'Model': 'gemini-2.5-flash', 'API key': 'configured', 'Circuit breaker': 'CLOSED', 'Tokens (24h)': 184230, 'Cost (this month)': '€12.40' },
    lastCheckedMinutes: 0, lastError: null, recentChecks: [{ at: '2026-06-18T06:30:00Z', ok: true, message: 'Probe OK' }] },
];

export const mockSystemHealthKpis = {
  apiLatencyMs: 142, errorRatePct: 0.2, requestsPerMin: 48, dbPoolUsed: 18, dbPoolMax: 40,
  redisMemUsedMb: 312, redisMemMaxMb: 1024, jobQueueDepth: 47,
  down: 1, degraded: 1, ok: 5, total: 7, uptimeMs: 536400000,
};

export const mockSystemHealthEvents = [
  { tone: 'warn', title: 'Mailbox paused', detail: 'Paused mailbox support@nordlicht.de', at: '2026-06-18T05:10:00Z' },
  { tone: 'ok', title: 'Subsystem probed', detail: 'Probed subsystem PostgreSQL', at: '2026-06-18T04:55:00Z' },
  { tone: 'warn', title: 'Cache flushed', detail: 'Flushed 3 cache region(s)', at: '2026-06-17T22:00:00Z' },
];

export const mockMaintenanceMode = { enabled: false, message: null, updatedAt: null };

export const mockSystemHealthDetail = mockSystemHealthSubsystems[2]; // redis (down) — has Flush cache action

// ── Plans & Subscriptions (GET /admin/subscriptions/*) ───────────────────
export const mockSubscriptions = {
  content: [
    {
      orgId: 'o1', orgName: 'Postwerk GmbH', slug: 'postwerk', personal: false,
      ownerName: 'Marius Kessler', ownerEmail: 'mk@postwerk.de', planName: 'PRO', status: 'active',
      memberCount: 8, mailboxCount: 4, automationCount: 12, aiCostMicrosThisMonth: 4800000,
      effectiveCapCents: 500, createdAt: '2024-02-01T00:00:00Z',
    },
    {
      orgId: 'o4', orgName: 'Aurora Logistik', slug: 'aurora-logistik', personal: false,
      ownerName: 'Lena Wagner', ownerEmail: 'lena@aurora-logistik.de', planName: 'ENTERPRISE', status: 'active',
      memberCount: 21, mailboxCount: 15, automationCount: 44, aiCostMicrosThisMonth: 61230000,
      effectiveCapCents: -1, createdAt: '2024-03-10T00:00:00Z',
    },
    {
      orgId: 'o5', orgName: 'Kleinbüro Schmidt', slug: 'kleinbuero', personal: true,
      ownerName: 'Jonas Schmidt', ownerEmail: 'jonas@example.de', planName: 'FREE', status: 'active',
      memberCount: 1, mailboxCount: 1, automationCount: 2, aiCostMicrosThisMonth: 0,
      effectiveCapCents: 0, createdAt: '2024-05-20T00:00:00Z',
    },
    {
      orgId: 'o6', orgName: 'Nordlicht Medien', slug: 'nordlicht', personal: false,
      ownerName: 'Carla Neumann', ownerEmail: 'carla@nordlicht.de', planName: 'PRO', status: 'suspended',
      memberCount: 5, mailboxCount: 3, automationCount: 7, aiCostMicrosThisMonth: 1200000,
      effectiveCapCents: 500, createdAt: '2024-04-02T00:00:00Z',
    },
  ],
  totalElements: 4,
  totalPages: 1,
  number: 0,
  size: 10,
};

export const mockSubscriptionKpis = {
  mrr: 487, activeSubscriptions: 18, aiCostCentsThisMonth: 9240, overCapCount: 2, planCount: 3,
};

export const mockSubscriptionDetail = {
  orgId: 'o1', orgName: 'Postwerk GmbH', slug: 'postwerk', personal: false,
  ownerName: 'Marius Kessler', ownerEmail: 'mk@postwerk.de', status: 'active',
  planId: 'p2', planName: 'PRO', planPrice: 29, planCostLimitCents: 500, effectiveCapCents: 500,
  aiCostMicrosThisMonth: 4800000, memberCount: 8, mailboxCount: 4, automationCount: 12,
  mailboxLimit: 20, automationLimit: 50, createdAt: '2024-02-01T00:00:00Z', suspendedAt: null,
  planHistory: [
    { at: '2026-04-01T10:00:00Z', detail: 'FREE → PRO', by: 'Carla Neumann' },
  ],
};

export const mockPlanHistory = mockSubscriptionDetail.planHistory;

// ── Background Jobs (GET /admin/background-jobs/*) ────────────────────────
export const mockJobs = [
  {
    id: 'data-retention', name: 'Data-retention sweep', type: 'Maintenance', scheduleHuman: 'daily 02:00',
    status: 'failing', lastRunAt: '2026-06-18T02:00:00Z', lastRunOk: false, lastDurationMs: 12800,
    nextRunAt: '2026-06-19T02:00:00Z', itemsLastRun: null, runsLast24h: 1, failedLast24h: 1,
    description: 'GDPR/DSGVO retention: prunes expired emails, users and conversations, and pseudonymizes IPs.', drainsQueueId: null,
  },
  {
    id: 'email-sync', name: 'Email-sync scheduler', type: 'Scheduler', scheduleHuman: 'every 5 min',
    status: 'healthy', lastRunAt: '2026-06-18T09:20:00Z', lastRunOk: true, lastDurationMs: 1420,
    nextRunAt: '2026-06-18T09:25:00Z', itemsLastRun: null, runsLast24h: 288, failedLast24h: 0,
    description: 'Pulls new mail from every read-enabled IMAP mailbox across all tenants.', drainsQueueId: null,
  },
  {
    id: 'automation-poller', name: 'Automation poller', type: 'Scheduler', scheduleHuman: 'every 60s',
    status: 'healthy', lastRunAt: '2026-06-18T09:23:00Z', lastRunOk: true, lastDurationMs: 340,
    nextRunAt: '2026-06-18T09:24:00Z', itemsLastRun: null, runsLast24h: 1440, failedLast24h: 0,
    description: 'Syncs accounts with active automations and processes new emails; drains the approval queue.', drainsQueueId: 'approval-queue',
  },
  {
    id: 'delayed-email', name: 'Delayed-email processor', type: 'Worker', scheduleHuman: 'every 30s',
    status: 'paused', lastRunAt: '2026-06-18T08:00:00Z', lastRunOk: true, lastDurationMs: 95,
    nextRunAt: null, itemsLastRun: null, runsLast24h: 2400, failedLast24h: 0,
    description: 'Resumes automation flows for delayed emails once their delay elapses.', drainsQueueId: 'delayed-queue',
  },
];

export const mockBackgroundJobsKpis = {
  scheduled: 4, runs24h: 4129, failed24h: 1, queueDepth: 47, avgDurationMs: 3664, nextRunMinutes: 1, paused: 1, failing: 1,
};

export const mockJobQueues = [
  { id: 'approval-queue', name: 'Approval queue', drainJobId: 'automation-poller', pending: 12, tone: 'clear',
    breakdown: [{ label: 'Pending', value: 12, dot: 'warn' }, { label: 'Approved', value: 340, dot: 'ok' }, { label: 'Rejected', value: 18, dot: 'danger' }] },
  { id: 'delayed-queue', name: 'Delayed-email queue', drainJobId: 'delayed-email', pending: 35, tone: 'clear',
    breakdown: [{ label: 'Awaiting send', value: 35, dot: 'warn' }] },
];

export const mockJobDetail = {
  job: mockJobs[0], // data-retention (failing) — shows the last-error callout
  recentRuns: [
    { at: '2026-06-18T02:00:00Z', ok: false, durationMs: 12800, message: 'FK violation on attachments_archive — rolled back', triggeredBy: 'schedule' },
    { at: '2026-06-17T02:00:00Z', ok: true, durationMs: 9400, message: null, triggeredBy: 'schedule' },
  ],
};

// ── Marketplace Moderation (GET /admin/marketplace/*) ─────────────────────
export const mockMarketplaceListings = {
  content: [
    {
      id: 'l1', name: 'Invoice auto-filer', slug: 'invoice-auto-filer', authorName: 'Marius Kessler',
      authorEmail: 'mk@postwerk.de', kind: 'AUTOMATION', pricingModel: 'FREE', price: 0, status: 'PUBLIC',
      featured: true, takenDown: false, installCount: 1840, ratingAvg: 4.6, ratingCount: 73,
      category: 'Finance', createdAt: '2025-11-02T09:00:00Z',
    },
    {
      id: 'l2', name: 'Stripe webhook bridge', slug: 'stripe-webhook-bridge', authorName: 'Lena Wagner',
      authorEmail: 'lena@aurora-logistik.de', kind: 'INTEGRATION', pricingModel: 'MONTHLY', price: 9, status: 'PUBLIC',
      featured: false, takenDown: false, installCount: 420, ratingAvg: 4.1, ratingCount: 28,
      category: 'Developer', createdAt: '2026-01-14T13:30:00Z',
    },
    {
      id: 'l3', name: 'Spammy lead blaster', slug: 'spammy-lead-blaster', authorName: 'Jonas Schmidt',
      authorEmail: 'jonas@example.de', kind: 'AUTOMATION', pricingModel: 'ONE_TIME', price: 49, status: 'TAKEN_DOWN',
      featured: false, takenDown: true, installCount: 12, ratingAvg: 1.8, ratingCount: 9,
      category: 'Sales', createdAt: '2026-03-21T08:15:00Z',
    },
    {
      id: 'l4', name: 'Internal triage helper', slug: 'internal-triage-helper', authorName: 'Carla Neumann',
      authorEmail: 'carla@nordlicht.de', kind: 'AUTOMATION', pricingModel: 'FREE', price: 0, status: 'PAUSED',
      featured: false, takenDown: false, installCount: 5, ratingAvg: 0, ratingCount: 0,
      category: null, createdAt: '2026-05-30T10:45:00Z',
    },
  ],
  totalElements: 4,
  totalPages: 1,
  number: 0,
  size: 10,
};

export const mockMarketplaceReviews = {
  content: [
    {
      id: 'rv1', listingId: 'l1', listingName: 'Invoice auto-filer', authorName: 'Tom Becker',
      authorEmail: 'tom@example.com', rating: 5, text: 'Saved me hours every week — flawless.', hidden: false,
      createdAt: '2026-05-10T11:00:00Z',
    },
    {
      id: 'rv2', listingId: 'l3', listingName: 'Spammy lead blaster', authorName: 'Anon User',
      authorEmail: 'anon@example.com', rating: 1, text: 'This just sends spam, total scam. Avoid!!!', hidden: false,
      createdAt: '2026-05-12T16:20:00Z',
    },
    {
      id: 'rv3', listingId: 'l2', listingName: 'Stripe webhook bridge', authorName: 'Dev Müller',
      authorEmail: 'dev@aurora-logistik.de', rating: 2, text: 'Docs are thin and setup broke twice.', hidden: true,
      createdAt: '2026-05-15T08:05:00Z',
    },
  ],
  totalElements: 3,
  totalPages: 1,
  number: 0,
  size: 10,
};

export const mockMarketplaceKpis = {
  totalListings: 4, publishedListings: 2, pausedListings: 1, takenDownListings: 1,
  totalInstalls: 2277, avgRating: 4.2, pendingListings: 1,
  totalReviews: 3, visibleReviews: 2, hiddenReviews: 1, reviewAvgRating: 2.7, lowRatings: 2, pendingReviews: 1,
};

// ── GDPR / Data Requests (GET /admin/gdpr/*) ──────────────────────────────
// Deadlines are computed relative to "now" so the overdue / due-soon / ok buckets
// stay deterministic whatever day the e2e run executes.
const GDPR_NOW = Date.now();
const gdprIso = (daysFromNow: number) => new Date(GDPR_NOW + daysFromNow * 86_400_000).toISOString();

export const mockGdprRequests = {
  content: [
    {
      id: 'dsr-4821', subjectName: 'Katrin Hofmann', subjectEmail: 'k.hofmann@gmx.de', org: 'Weber & Söhne GmbH',
      type: 'ERASURE', status: 'PENDING', channel: 'EMAIL', requestedAt: gdprIso(-34), deadlineAt: gdprIso(-4),
      closedAt: null, handlerName: 'Lena Vogt', note: 'Art. 17 erasure — former employee, account offboarded.', rejectReason: null,
    },
    {
      id: 'dsr-4806', subjectName: 'Tobias Reinhardt', subjectEmail: 't.reinhardt@outlook.com', org: 'Reinhardt Consulting',
      type: 'EXPORT', status: 'IN_PROGRESS', channel: 'IN_APP', requestedAt: gdprIso(-26), deadlineAt: gdprIso(4),
      closedAt: null, handlerName: 'Marius Kessler', note: 'Art. 15 / 20 access + portability.', rejectReason: null,
    },
    {
      id: 'dsr-4717', subjectName: 'Elias Brunner', subjectEmail: 'e.brunner@bluewin.ch', org: 'Brunner AG',
      type: 'ACCESS', status: 'PENDING', channel: 'POST', requestedAt: gdprIso(-5), deadlineAt: gdprIso(25),
      closedAt: null, handlerName: null, note: 'Art. 15 access — written request received by post.', rejectReason: null,
    },
    {
      id: 'dsr-4698', subjectName: 'Ana Costa', subjectEmail: 'ana.costa@sapo.pt', org: 'Costa Design',
      type: 'EXPORT', status: 'COMPLETED', channel: 'IN_APP', requestedAt: gdprIso(-20), deadlineAt: gdprIso(10),
      closedAt: gdprIso(-6), handlerName: 'Marius Kessler', note: 'Art. 15 access — delivered as encrypted archive.', rejectReason: null,
    },
  ],
  totalElements: 4,
  totalPages: 1,
  number: 0,
  size: 10,
};

export const mockGdprKpis = {
  open: 3, overdue: 1, dueSoon: 1, closed30d: 1, avgCloseDays: 10, pending: 2, inProgress: 1,
};

export const mockGdprRetention = {
  emailDays: 365, conversationDays: 90, ipDays: 90, auditDays: 730, lastSweepAt: gdprIso(0),
};

export const mockGdprDetail = {
  request: mockGdprRequests.content[0], // Katrin Hofmann — ERASURE, overdue
  footprint: { mailboxes: 1, emails: 3120, automations: 4, conversations: 86, auditEntries: 214 },
  timeline: [
    { label: 'Request logged (email)', actor: 'system', at: gdprIso(-34) },
    { label: 'Identity verified (signed letter + ID)', actor: 'Lena Vogt', at: gdprIso(-33) },
    { label: 'Footprint assembled · awaiting erasure approval', actor: 'Lena Vogt', at: gdprIso(-31) },
  ],
};

export const mockMarketplaceListingDetail = {
  listing: mockMarketplaceListings.content[0], // Invoice auto-filer (PUBLIC, featured)
  description: 'Automatically files incoming invoices into the right folder and extracts totals into a parameter set.',
  reviews: [
    mockMarketplaceReviews.content[0],
    { id: 'rv9', listingId: 'l1', listingName: 'Invoice auto-filer', authorName: 'Hidden Critic',
      authorEmail: 'hc@example.com', rating: 2, text: 'Hidden by staff — abusive language.', hidden: true,
      createdAt: '2026-05-09T07:00:00Z' },
  ],
};

// ── Announcements (GET /admin/announcements/*) ────────────────────────────
const ANN_NOW = Date.now();
const annIso = (daysFromNow: number) => new Date(ANN_NOW + daysFromNow * 86_400_000).toISOString();

export const mockAnnouncements = {
  content: [
    {
      id: 'ann-2041', titleDe: 'Geplante Wartung am Mailversand', titleEn: 'Scheduled maintenance on mail delivery',
      bodyDe: 'Heute Abend.', bodyEn: 'Outbound mail delivery will be briefly paused tonight.',
      ctaLabelDe: 'Statusseite', ctaLabelEn: 'Status page', ctaUrl: 'https://status.postwerk.eu',
      type: 'MAINTENANCE', placement: 'BANNER', audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null, audienceOrgName: null,
      dismissible: false, lifecycle: 'PUBLISHED', status: 'LIVE', startsAt: annIso(-0.1), endsAt: annIso(0.2),
      createdByName: 'Marius Kessler', updatedByName: 'Marius Kessler', updatedAt: annIso(0),
    },
    {
      id: 'ann-2015', titleDe: 'Neue Preise ab 1. Juli', titleEn: 'New pricing from 1 July',
      bodyDe: 'Ab dem 1. Juli.', bodyEn: 'We are updating our plans from 1 July.',
      ctaLabelDe: null, ctaLabelEn: null, ctaUrl: null,
      type: 'INFO', placement: 'LOGIN', audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null, audienceOrgName: null,
      dismissible: true, lifecycle: 'PUBLISHED', status: 'SCHEDULED', startsAt: annIso(6), endsAt: annIso(30),
      createdByName: 'Sofia Lindqvist', updatedByName: 'Sofia Lindqvist', updatedAt: annIso(-4),
    },
    {
      id: 'ann-2007', titleDe: 'Marktplatz: 100 neue Vorlagen', titleEn: 'Marketplace: 100 new templates',
      bodyDe: 'Hundert neue Vorlagen.', bodyEn: 'A hundred fresh templates are now live.',
      ctaLabelDe: null, ctaLabelEn: null, ctaUrl: null,
      type: 'SUCCESS', placement: 'TOAST', audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null, audienceOrgName: null,
      dismissible: true, lifecycle: 'DRAFT', status: 'DRAFT', startsAt: null, endsAt: null,
      createdByName: 'Marius Kessler', updatedByName: 'Marius Kessler', updatedAt: annIso(-1),
    },
    {
      id: 'ann-1994', titleDe: 'Notfallwartung am 2. Juni', titleEn: 'Emergency maintenance on 2 June',
      bodyDe: 'Ungeplante Wartung.', bodyEn: 'We carried out unplanned maintenance.',
      ctaLabelDe: null, ctaLabelEn: null, ctaUrl: null,
      type: 'WARNING', placement: 'BANNER', audience: 'PLAN', audiencePlans: ['ENTERPRISE'], audienceOrgId: null, audienceOrgName: null,
      dismissible: true, lifecycle: 'PUBLISHED', status: 'EXPIRED', startsAt: annIso(-16), endsAt: annIso(-15),
      createdByName: 'Jonas Brandt', updatedByName: 'Jonas Brandt', updatedAt: annIso(-15),
    },
  ],
  totalElements: 4, totalPages: 1, number: 0, size: 10,
};

export const mockAnnouncementKpis = {
  live: 1, scheduled: 1, drafts: 1, maintenanceLive: 1, expired30d: 1, nextLiveAt: annIso(6),
};

export const mockAnnouncementDetail = {
  announcement: mockAnnouncements.content[0],
  history: [
    { label: 'Draft created', actor: 'Marius Kessler', at: annIso(-2) },
    { label: 'Content / settings edited', actor: 'Marius Kessler', at: annIso(-1) },
    { label: 'Published', actor: 'Marius Kessler', at: annIso(0) },
  ],
};

// ── Feature Flags (GET /admin/feature-flags/*) ────────────────────────────
const FF_NOW = Date.now();
const ffIso = (daysFromNow: number) => new Date(FF_NOW + daysFromNow * 86_400_000).toISOString();

export const mockFlags = {
  content: [
    {
      id: 'ff-118', key: 'marketplace.publish', name: 'Marketplace publishing', description: 'Lets authors publish to the marketplace.',
      kind: 'RELEASE', enabled: true, rollout: 100, audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null, audienceOrgName: null,
      overrides: [], killed: false, archived: false, stale: true, status: 'ON', updatedByName: 'Marius Kessler', updatedAt: ffIso(-214),
    },
    {
      id: 'ff-204', key: 'wizard.v2', name: 'AI automation wizard v2', description: 'The redesigned step-by-step wizard.',
      kind: 'RELEASE', enabled: true, rollout: 35, audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null, audienceOrgName: null,
      overrides: [{ scope: 'Staff', value: 'on' }], killed: false, archived: false, stale: false, status: 'ROLLING', updatedByName: 'Lena Vogt', updatedAt: ffIso(-2),
    },
    {
      id: 'ff-309', key: 'email.failover_provider', name: 'Email failover provider', description: 'Kill-switch diverting outbound mail to the backup ESP.',
      kind: 'OPS', enabled: false, rollout: 0, audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null, audienceOrgName: null,
      overrides: [], killed: true, archived: false, stale: false, status: 'KILLED', updatedByName: 'Jonas Brandt', updatedAt: ffIso(0),
    },
    {
      id: 'ff-261', key: 'billing.new_invoices', name: 'Redesigned invoices', description: 'New invoice PDF — pending finance sign-off.',
      kind: 'RELEASE', enabled: false, rollout: 0, audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null, audienceOrgName: null,
      overrides: [], killed: false, archived: false, stale: false, status: 'OFF', updatedByName: 'Sofia Lindqvist', updatedAt: ffIso(-7),
    },
  ],
  totalElements: 4, totalPages: 1, number: 0, size: 10,
};

export const mockFlagKpis = {
  total: 4, on: 1, partial: 1, off: 1, killed: 1, archived: 0, stale: 1, inFlight: 1,
};

export const mockFlagDetail = {
  flag: mockFlags.content[1], // wizard.v2 — ROLLING
  history: [
    { label: 'Created · 0% → off', actor: 'Lena Vogt', at: ffIso(-21) },
    { label: 'Rollout 0% → 10%', actor: 'Lena Vogt', at: ffIso(-12) },
    { label: 'Rollout 10% → 35%', actor: 'Lena Vogt', at: ffIso(-2) },
  ],
};

// ── Staff & Roles (GET /admin/staff/*) ────────────────────────────────────
const SF_NOW = Date.now();
const sfIso = (daysAgo: number) => new Date(SF_NOW - daysAgo * 86_400_000).toISOString();

export const mockStaffRoster = {
  content: [
    { id: 'st-001', name: 'Marius Kessler', email: 'marius.kessler@postwerk.io', role: 'SUPER_ADMIN', tier: 'PRIVILEGED', capabilityCount: 23, lastActiveAt: sfIso(0), staffSince: sfIso(760), self: true },
    { id: 'st-014', name: 'Lena Vogt', email: 'lena.vogt@postwerk.io', role: 'ADMIN', tier: 'PRIVILEGED', capabilityCount: 21, lastActiveAt: sfIso(0), staffSince: sfIso(430), self: false },
    { id: 'st-031', name: 'Chiara Ferrari', email: 'chiara.ferrari@postwerk.io', role: 'BILLING', tier: 'PRIVILEGED', capabilityCount: 10, lastActiveAt: sfIso(2), staffSince: sfIso(158), self: false },
    { id: 'st-038', name: 'Sofia Lindqvist', email: 'sofia.lindqvist@postwerk.io', role: 'MODERATOR', tier: 'PRIVILEGED', capabilityCount: 5, lastActiveAt: sfIso(0), staffSince: sfIso(246), self: false },
    { id: 'st-051', name: 'Mateusz Nowak', email: 'mateusz.nowak@postwerk.io', role: 'SUPPORT', tier: 'READ_ONLY', capabilityCount: 12, lastActiveAt: sfIso(0), staffSince: sfIso(190), self: false },
    { id: 'st-063', name: 'Tomáš Novák', email: 'tomas.novak@postwerk.io', role: 'AUDITOR', tier: 'READ_ONLY', capabilityCount: 10, lastActiveAt: sfIso(6), staffSince: sfIso(96), self: false },
  ],
  totalElements: 6, totalPages: 1, number: 0, size: 10,
};

export const mockStaffKpis = { total: 6, superAdmins: 1, privileged: 4, readOnly: 2, added30d: 0 };

const SF_ALL = [
  'PLATFORM_DASHBOARD_VIEW', 'USER_VIEW', 'USER_MANAGE', 'USER_CREDENTIAL_RESET', 'ORG_VIEW', 'ORG_MANAGE',
  'PLAN_VIEW', 'PLAN_MANAGE', 'BILLING_VIEW', 'BILLING_MANAGE', 'QUOTA_OVERRIDE', 'AI_USAGE_VIEW',
  'AUTOMATION_OVERSIGHT_VIEW', 'PROMPT_MANAGE', 'INFRA_VIEW', 'INFRA_MANAGE', 'MARKETPLACE_MODERATE',
  'COMPLIANCE_VIEW', 'COMPLIANCE_MANAGE', 'AUDIT_LOG_VIEW', 'FEATURE_FLAG_MANAGE', 'ANNOUNCEMENT_MANAGE', 'STAFF_MANAGE',
];
export const mockStaffRolesMatrix = {
  roles: [
    { key: 'SUPER_ADMIN', tier: 'PRIVILEGED', privileged: true, permissions: [...SF_ALL] },
    { key: 'ADMIN', tier: 'PRIVILEGED', privileged: true, permissions: SF_ALL.filter(c => c !== 'STAFF_MANAGE' && c !== 'PROMPT_MANAGE') },
    { key: 'BILLING', tier: 'PRIVILEGED', privileged: true, permissions: ['PLATFORM_DASHBOARD_VIEW', 'USER_VIEW', 'ORG_VIEW', 'PLAN_VIEW', 'PLAN_MANAGE', 'BILLING_VIEW', 'BILLING_MANAGE', 'QUOTA_OVERRIDE', 'AI_USAGE_VIEW', 'AUDIT_LOG_VIEW'] },
    { key: 'MODERATOR', tier: 'PRIVILEGED', privileged: true, permissions: ['PLATFORM_DASHBOARD_VIEW', 'USER_VIEW', 'ORG_VIEW', 'MARKETPLACE_MODERATE', 'AUDIT_LOG_VIEW'] },
    { key: 'SUPPORT', tier: 'READ_ONLY', privileged: false, permissions: ['PLATFORM_DASHBOARD_VIEW', 'USER_VIEW', 'USER_CREDENTIAL_RESET', 'ORG_VIEW', 'PLAN_VIEW', 'BILLING_VIEW', 'QUOTA_OVERRIDE', 'AI_USAGE_VIEW', 'AUTOMATION_OVERSIGHT_VIEW', 'INFRA_VIEW', 'COMPLIANCE_VIEW', 'AUDIT_LOG_VIEW'] },
    { key: 'AUDITOR', tier: 'READ_ONLY', privileged: false, permissions: ['PLATFORM_DASHBOARD_VIEW', 'USER_VIEW', 'ORG_VIEW', 'PLAN_VIEW', 'BILLING_VIEW', 'AI_USAGE_VIEW', 'AUTOMATION_OVERSIGHT_VIEW', 'INFRA_VIEW', 'COMPLIANCE_VIEW', 'AUDIT_LOG_VIEW'] },
  ],
  allPermissions: [...SF_ALL],
};

export const mockStaffCandidates = [
  { id: 'u-101', name: 'Hannah Weber', email: 'hannah.weber@weber-soehne.de' },
  { id: 'u-102', name: 'Lukas Reinhardt', email: 'lukas@reinhardt-consulting.com' },
];
