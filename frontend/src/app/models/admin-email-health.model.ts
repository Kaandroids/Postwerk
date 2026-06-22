import { Page } from './page.model';

export type { Page };

/** Derived connection health of a mailbox (never-synced mailboxes report 'ok', pending first sync). */
export type MailboxHealth = 'ok' | 'failing' | 'auth_error';

/** Per-cluster status for the by-cluster summary row. */
export type ClusterStatus = 'ok' | 'warn' | 'down';

/** One mailbox row in the Email Health list (admin). Carries only safe metadata — no credentials. */
export interface MailboxHealthRow {
  id: string;
  email: string;
  displayName: string | null;
  color: string;
  ownerOrgId: string | null;
  ownerOrgName: string | null;
  ownerEmail: string | null;
  protocols: string[];           // subset of ['IMAP','SMTP']
  health: MailboxHealth;
  paused: boolean;
  lastSyncAt: string | null;
  syncAgoMinutes: number | null; // null = never synced
  stale: boolean;
  lastError: string | null;
  server: string;                // IMAP host = relay cluster
  queueDepth: number | null;     // always null (no send queue)
  imapConfigured: boolean;
  smtpConfigured: boolean;
}

/** A single sync attempt in a mailbox's recent-attempts timeline. */
export interface MailboxSyncAttempt {
  at: string | null;
  ok: boolean;
  message: string;
}

/** Full mailbox detail for the Email Health detail modal. */
export interface MailboxHealthDetail {
  id: string;
  email: string;
  displayName: string | null;
  color: string;
  ownerOrgId: string | null;
  ownerOrgName: string | null;
  ownerEmail: string | null;
  protocols: string[];
  health: MailboxHealth;
  paused: boolean;
  imapHost: string | null;
  imapPort: number | null;
  imapSsl: boolean | null;
  readEnabled: boolean;
  smtpHost: string | null;
  smtpPort: number | null;
  smtpSsl: boolean | null;
  sendEnabled: boolean;
  server: string;
  lastSyncAt: string | null;
  syncAgoMinutes: number | null;
  stale: boolean;
  lastError: string | null;
  lastErrorAt: string | null;
  queueDepth: number | null;
  createdAt: string;
  recentAttempts: MailboxSyncAttempt[];
}

/** KPI strip totals (GET /admin/email-health/kpis). */
export interface EmailHealthKpis {
  total: number;
  healthy: number;
  failing: number;
  authErrors: number;
  paused: number;
  avgSyncLagMinutes: number | null;
}

/** Per-cluster summary card (GET /admin/email-health/clusters). */
export interface EmailClusterSummary {
  host: string;
  healthy: number;
  total: number;
  failing: number;
  bad: number;
  status: ClusterStatus;
}

/** Filters passed to the list endpoint (all optional; empty = "All"). */
export interface EmailHealthFilters {
  search?: string;
  protocol?: '' | 'IMAP' | 'SMTP';
  health?: '' | MailboxHealth | 'paused';
  server?: string;
  sync?: '' | 'recent' | 'stale';
}
