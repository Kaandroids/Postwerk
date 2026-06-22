/**
 * Analytics (#analytics) — types mirroring the backend `AnalyticsOverviewResponse` /
 * `AutomationAnalyticsResponse` DTOs. Org-scoped automation performance + AI cost over a
 * 7d / 30d / 90d window. Money is in integer euro-cents; dates are ISO `YYYY-MM-DD`.
 */

export type AnalyticsRange = '7d' | '30d' | '90d';

export interface TrendPoint {
  date: string;     // YYYY-MM-DD
  total: number;
  success: number;
  failed: number;
}

export interface CostSlice {
  key: string;      // operation (CLASSIFY/…) or model name
  costCents: number;
  tokens: number;
}

export interface DailyCost {
  date: string;
  cents: number;
}

export interface AnalyticsAiCost {
  totalCents: number;
  byOperation: CostSlice[];
  byModel: CostSlice[];
  dailyCents: DailyCost[];
}

export interface TopAutomation {
  id: string;
  name: string;
  color: string | null;
  runs: number;
  successRate: number;
  failedRuns: number;
  lastRunAt: string | null;
}

export interface FailureRow {
  nodeType: string;
  failures: number;
  total: number;
  failRate: number;
}

export interface ApprovalStats {
  pending: number;
  approved: number;
  rejected: number;
  expired: number;
  avgDecisionMinutes: number | null;
}

export interface AnalyticsKpis {
  totalRuns: number;
  runsSeries: number[];
  successRate: number;
  emailsProcessed: number;
  processedPct: number;
  failedRuns: number;
  failRate: number;
  failsSeries: number[];
  aiCostCents: number;
  costSeries: number[];
  costCapCents: number | null;
  pendingApprovals: number;
  avgDecisionMinutes: number | null;
  deltas: { runs: number | null; cost: number | null };
}

export interface AnalyticsOverview {
  range: AnalyticsRange;
  days: number;
  activeAutomations: number;
  kpis: AnalyticsKpis;
  executionTrend: TrendPoint[];
  aiCost: AnalyticsAiCost;
  topAutomations: TopAutomation[];
  failureAnalysis: FailureRow[];
  approvals: ApprovalStats;
}

export interface AnalyticsAutomationInfo {
  id: string;
  name: string;
  color: string | null;
  status: string;
  kind: string;
  lastRunAt: string | null;
}

export interface AutomationDetailKpis {
  runs: number;
  successRate: number;
  failedRuns: number;
  failRate: number;
  emailsProcessed: number;
  processedPct: number;
  /** This automation's share of the org's AI usage (%), money-free. null when org has no runs. */
  aiSharePct: number | null;
  runsSeries: number[];
  failsSeries: number[];
}

export interface NodeFailure {
  nodeId: string;
  nodeLabel: string;
  nodeType: string;
  failures: number;
  total: number;
  failRate: number;
}

export interface RecentRun {
  traceId: string;
  status: string;
  startedAt: string;
  durationMs: number | null;
  emailSubject: string | null;
  emailFrom: string | null;
  errorMessage: string | null;
}

export interface AutomationAnalyticsDetail {
  automation: AnalyticsAutomationInfo;
  range: AnalyticsRange;
  days: number;
  kpis: AutomationDetailKpis;
  trend: TrendPoint[];
  nodeFailures: NodeFailure[];
  recentRuns: RecentRun[];
}

/** Tone tokens for the data-tone color system (icons, bars, donut arcs, pills). */
export type AnalyticsTone = 'accent' | 'violet' | 'success' | 'warning' | 'danger';

/** Donut/legend slice palette order — cycles through the tone system. */
export const COST_TONES: AnalyticsTone[] = ['accent', 'violet', 'success', 'warning', 'danger'];

/** CSS color expression for a tone token (violet maps to the analytics-only --tone-violet). */
export function toneVar(tone: AnalyticsTone): string {
  return tone === 'violet' ? 'var(--tone-violet)' : `var(--${tone})`;
}
