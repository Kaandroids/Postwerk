/**
 * Generic Spring Data {@code Page<T>} response envelope. Mirrors the JSON shape returned by
 * paginated backend endpoints, replacing the per-resource page interfaces (AdminUserPage,
 * AutomationExecutionPage, AdminAuditLogPage, AuditLogPage, …) that all repeated this structure.
 */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
