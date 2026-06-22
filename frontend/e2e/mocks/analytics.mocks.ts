/** Mock data for the analytics surface (/dashboard/analytics + /:id detail). */

export const mockAnalyticsOverview = {
  range: '30d',
  days: 30,
  activeAutomations: 3,
  kpis: {
    totalRuns: 1280,
    runsSeries: [10, 20, 15, 30, 25, 40, 35],
    successRate: 94,
    emailsProcessed: 980,
    processedPct: 76,
    failedRuns: 77,
    failRate: 6,
    failsSeries: [1, 2, 0, 3, 1, 2, 1],
    aiCostCents: 420,
    costSeries: [5, 8, 6, 10, 7, 9, 8],
    costCapCents: 500,
    pendingApprovals: 4,
    avgDecisionMinutes: 12,
    deltas: { runs: 8, cost: -3 },
  },
  executionTrend: [
    { date: '2026-06-15', total: 100, success: 95, failed: 5 },
    { date: '2026-06-16', total: 120, success: 112, failed: 8 },
    { date: '2026-06-17', total: 110, success: 104, failed: 6 },
  ],
  aiCost: {
    totalCents: 420,
    byOperation: [
      { key: 'classify', costCents: 200, tokens: 5000 },
      { key: 'extract', costCents: 220, tokens: 6000 },
    ],
    byModel: [{ key: 'gemini-2.5-flash', costCents: 420, tokens: 11000 }],
    dailyCents: [
      { date: '2026-06-15', cents: 60 },
      { date: '2026-06-16', cents: 80 },
    ],
  },
  topAutomations: [
    { id: 'auto-1', name: 'Invoice Router', color: '#6366f1', runs: 600, successRate: 96, failedRuns: 24, lastRunAt: '2026-06-21T08:00:00Z' },
    { id: 'auto-2', name: 'Spam Filter', color: '#ef4444', runs: 400, successRate: 90, failedRuns: 40, lastRunAt: '2026-06-21T07:00:00Z' },
  ],
  failureAnalysis: [
    { nodeType: 'WEBHOOK', failures: 30, total: 200, failRate: 15 },
    { nodeType: 'EMAIL_ACTION', failures: 10, total: 500, failRate: 2 },
  ],
  approvals: { pending: 4, approved: 120, rejected: 8, expired: 2, avgDecisionMinutes: 12 },
};

/** Overview with zero runs → drives the empty state (kpis.totalRuns === 0). */
export const mockAnalyticsOverviewEmpty = {
  ...mockAnalyticsOverview,
  activeAutomations: 0,
  kpis: { ...mockAnalyticsOverview.kpis, totalRuns: 0 },
  topAutomations: [],
};

export const mockAnalyticsDetail = {
  automation: {
    id: 'auto-1', name: 'Invoice Router', color: '#6366f1',
    status: 'ACTIVE', kind: 'AUTOMATION', lastRunAt: '2026-06-21T08:00:00Z',
  },
  range: '30d',
  days: 30,
  kpis: {
    runs: 600, successRate: 96, failedRuns: 24, failRate: 4,
    emailsProcessed: 580, processedPct: 80, aiSharePct: 47,
    runsSeries: [10, 20, 30, 25], failsSeries: [1, 0, 2, 1],
  },
  trend: [
    { date: '2026-06-15', total: 50, success: 48, failed: 2 },
    { date: '2026-06-16', total: 60, success: 58, failed: 2 },
  ],
  nodeFailures: [
    { nodeId: 'n1', nodeLabel: 'Forward', nodeType: 'EMAIL_ACTION', failures: 4, total: 100, failRate: 4 },
  ],
  recentRuns: [
    { traceId: 'tr-1', status: 'SUCCESS', startedAt: '2026-06-21T08:00:00Z', durationMs: 1200, emailSubject: 'Invoice #1', emailFrom: 'v@x.com', errorMessage: null },
    { traceId: 'tr-2', status: 'FAILED', startedAt: '2026-06-21T07:00:00Z', durationMs: null, emailSubject: 'Invoice #2', emailFrom: 'w@x.com', errorMessage: 'SMTP timeout' },
  ],
};
