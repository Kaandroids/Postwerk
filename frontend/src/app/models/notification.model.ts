/** Notification system frontend types (mirrors backend DTOs). See doc/NOTIFICATION_SYSTEM_DESIGN.md. */

export type NotificationSeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL' | 'ACTION_REQUIRED';

export type NotificationCategory =
  | 'AUTOMATION' | 'APPROVAL' | 'QUOTA' | 'MAILBOX' | 'TEAM' | 'MARKETPLACE' | 'SYSTEM';

/** A single notification in the user's inbox. Text renders from titleKey/bodyKey + params (i18n). */
export interface NotificationItem {
  id: string;
  category: NotificationCategory;
  type: string;
  severity: NotificationSeverity;
  titleKey: string;
  bodyKey: string | null;
  params: Record<string, unknown>;
  linkUrl: string | null;
  payload: Record<string, unknown>;
  organizationId: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationListResponse {
  items: NotificationItem[];
  unreadCount: number;
  total: number;
}

export interface UnreadCountResponse {
  count: number;
}

/** One row of the preference matrix: a category and its per-channel toggles. */
export interface NotificationPreference {
  category: string;
  inApp: boolean;
  email: boolean;
}
