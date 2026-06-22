import { Page } from './page.model';

export type { Page };

export type GdprType = 'EXPORT' | 'ERASURE' | 'RECTIFICATION' | 'RESTRICTION' | 'ACCESS';
export type GdprStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED';
export type GdprChannel = 'EMAIL' | 'IN_APP' | 'POST' | 'PHONE';
/** Derived from deadlineAt vs now (open requests only). */
export type DeadlineState = 'overdue' | 'due-soon' | 'ok';

/** A data-subject access request (DSAR) row. */
export interface GdprRequest {
  id: string;
  subjectName: string;
  subjectEmail: string;
  org: string | null;
  type: GdprType;
  status: GdprStatus;
  channel: GdprChannel;
  requestedAt: string;
  deadlineAt: string;
  closedAt: string | null;
  handlerName: string | null;
  note: string | null;
  rejectReason: string | null;
}

/** Read-only counts of the subject's stored data. */
export interface GdprFootprint {
  mailboxes: number;
  emails: number;
  automations: number;
  conversations: number;
  auditEntries: number;
}

export interface GdprTimelineEntry {
  label: string;
  actor: string; // staff name, or "system"
  at: string;
}

/** A request + its footprint + timeline (detail modal). */
export interface GdprRequestDetail {
  request: GdprRequest;
  footprint: GdprFootprint;
  timeline: GdprTimelineEntry[];
}

export interface GdprKpis {
  open: number;
  overdue: number;
  dueSoon: number;
  closed30d: number;
  avgCloseDays: number | null;
  pending: number;
  inProgress: number;
}

/** Standing automated retention posture (real GdprProperties + nightly sweep). */
export interface GdprRetention {
  emailDays: number;
  conversationDays: number;
  ipDays: number;
  auditDays: number;
  lastSweepAt: string | null;
}

export interface GdprFilters {
  search?: string;
  type?: '' | GdprType;
  status?: '' | GdprStatus;
  deadline?: '' | 'overdue' | 'due-soon';
  sort?: '' | 'requested' | 'deadline' | 'status';
  dir?: 'asc' | 'desc';
}

export interface CreateGdprRequest {
  subjectEmail: string;
  subjectName: string;
  type: GdprType;
  channel: GdprChannel;
  note: string;
}
