/** Job health status. */
export type JobStatus = 'healthy' | 'failing' | 'paused';

/** One execution in a job's recent-runs timeline. */
export interface JobRun {
  at: string;
  ok: boolean;
  durationMs: number;
  message: string | null;
  triggeredBy: string; // schedule | manual
}

/** One recurring platform job (admin Background Jobs). */
export interface Job {
  id: string;
  name: string;
  type: string; // Scheduler | Worker | Maintenance
  scheduleHuman: string;
  status: JobStatus;
  lastRunAt: string | null;
  lastRunOk: boolean | null;
  lastDurationMs: number | null;
  nextRunAt: string | null;
  itemsLastRun: number | null;
  runsLast24h: number;
  failedLast24h: number;
  description: string;
  drainsQueueId: string | null;
}

/** A job + its recent-runs timeline (detail modal). */
export interface JobDetail {
  job: Job;
  recentRuns: JobRun[];
}

/** A labelled count within a queue. */
export interface QueueBreakdown {
  label: string;
  value: number;
  dot: 'ok' | 'warn' | 'danger';
}

/** A work queue summary. */
export interface JobQueue {
  id: string;
  name: string;
  drainJobId: string;
  pending: number;
  tone: 'backlog' | 'clear';
  breakdown: QueueBreakdown[];
}

/** KPI strip totals (GET /admin/background-jobs/kpis). */
export interface BackgroundJobsKpis {
  scheduled: number;
  runs24h: number;
  failed24h: number;
  queueDepth: number;
  avgDurationMs: number | null;
  nextRunMinutes: number | null;
  paused: number;
  failing: number;
}

/** Client-side filters. */
export interface JobFilters {
  search?: string;
  type?: '' | 'Scheduler' | 'Worker' | 'Maintenance';
  status?: '' | JobStatus;
  lastRun?: '' | 'succeeded' | 'failed' | 'never';
}
