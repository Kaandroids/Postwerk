import { Page } from './page.model';

/** A single audit log entry recording a user action with metadata. */
export interface AuditLog {
  id: string;
  /** The acting member (null for system/retention actions). Used for the org-wide member filter. */
  userId: string | null;
  /** Display name of the acting member (full name or email). */
  userName: string | null;
  action: string;
  detail: string | null;
  ipAddress: string | null;
  createdAt: string;
}

/** Paginated response of audit log entries with total counts. */
export type AuditLogPage = Page<AuditLog>;
