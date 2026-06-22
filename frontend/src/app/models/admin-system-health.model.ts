/** Subsystem health status. */
export type SubsystemStatus = 'ok' | 'degraded' | 'down';

/** A single health-probe result in a subsystem's recent-checks timeline. */
export interface SubsystemCheck {
  at: string | null;
  ok: boolean;
  message: string;
}

/** One platform subsystem's live health (admin System Health). */
export interface Subsystem {
  id: string;
  name: string;
  kind: string;
  version: string | null;
  status: SubsystemStatus;
  primary: string;
  /** Per-kind ordered label→value map (rendered as field rows). */
  metrics: Record<string, string | number | boolean | null>;
  lastCheckedMinutes: number | null;
  lastError: string | null;
  recentChecks: SubsystemCheck[];
}

/** KPI strip totals (GET /admin/system-health/kpis). */
export interface SystemHealthKpis {
  apiLatencyMs: number | null;
  errorRatePct: number | null;
  requestsPerMin: number | null;
  dbPoolUsed: number | null;
  dbPoolMax: number | null;
  redisMemUsedMb: number | null;
  redisMemMaxMb: number | null;
  jobQueueDepth: number | null;
  down: number;
  degraded: number;
  ok: number;
  total: number;
  uptimeMs: number;
}

/** A recent platform event for the System Health timeline. */
export interface SystemHealthEvent {
  tone: 'ok' | 'warn' | 'danger';
  title: string;
  detail: string | null;
  at: string;
}

/** Platform maintenance-mode state. */
export interface MaintenanceMode {
  enabled: boolean;
  message: string | null;
  updatedAt: string | null;
}
