import { Page } from './page.model';

export interface AdminStats {
  totalUsers: number;
  activeUsers: number;
  deletedUsers: number;
  newUsersLast7Days: number;
  newUsersLast30Days: number;
  totalPromptTokens: number;
  totalOutputTokens: number;
  totalAutomationExecutions: number;
  successfulExecutions: number;
  failedExecutions: number;
  activeAutomations: number;
  totalEmails: number;
}

export interface AdminUser {
  id: string;
  email: string;
  fullName: string;
  company: string | null;
  role: string;
  staffRole: string | null;
  lastLoginAt: string | null;
  lastLoginIp: string | null;
  createdAt: string;
  deleted: boolean;
  emailAccountCount: number;
  automationCount: number;
  totalTokensUsed: number;
  /** Name of the user's effective plan (their personal org's plan), or null if none. */
  planName: string | null;
  /** Number of active organization memberships the user holds. */
  orgCount: number;
  /** AI cost incurred since the start of this UTC month, in micros (1 USD = 1M micros). */
  aiCostMicrosThisMonth: number;
  /** Plan AI cap in cents: -1 unlimited, 0 disabled, >0 monthly cent cap. */
  costLimitCents: number;
}

export type AdminUserPage = Page<AdminUser>;

export interface AiUsageStats {
  totalPromptTokens: number;
  totalOutputTokens: number;
  totalTokens: number;
  totalBillableChars: number;
  totalCostCents: number;
  byModel: ModelBreakdown[];
  byOperation: OperationBreakdown[];
}

export interface ModelBreakdown {
  model: string;
  promptTokens: number;
  outputTokens: number;
  totalTokens: number;
}

export interface OperationBreakdown {
  operation: string;
  promptTokens: number;
  outputTokens: number;
  totalTokens: number;
}

export interface AiUsageByUser {
  userId: string;
  email: string;
  fullName: string;
  promptTokens: number;
  outputTokens: number;
  totalTokens: number;
  requestCount: number;
  costCents: number;
}

export interface TimelineDataPoint {
  date: string;
  value: number;
}

export interface AutomationStats {
  totalExecutions: number;
  successCount: number;
  failedCount: number;
  runningCount: number;
  successRate: number;
  activeAutomations: number;
  totalAutomations: number;
  topAutomations: TopAutomation[];
}

export interface TopAutomation {
  automationId: string;
  automationName: string;
  executionCount: number;
  successCount: number;
  failedCount: number;
}

export interface AutomationExecution {
  id: string;
  automationId: string;
  automationName: string;
  status: string;
  processedCount: number;
  errorLog: string | null;
  triggeredAt: string;
  completedAt: string | null;
}

export type AutomationExecutionPage = Page<AutomationExecution>;

export interface AdminAuditLog {
  id: string;
  userId: string | null;
  userEmail: string | null;
  userName: string | null;
  action: string;
  detail: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export type AdminAuditLogPage = Page<AdminAuditLog>;

/** An organization membership held by a user (admin Users detail → Organizations tab). */
export interface AdminUserOrg {
  orgId: string;
  name: string;
  slug: string;
  personal: boolean;
  role: string;
  status: string;
  joinedAt: string;
}

/** An internal staff-only note about a user (admin Users detail → Notes tab). */
export interface StaffNote {
  id: string;
  body: string;
  authorName: string | null;
  authorEmail: string | null;
  createdAt: string;
}

/** Active session count for a user (admin Users detail → Sessions & Security tab). */
export interface AdminUserSessions {
  activeSessions: number;
}

/** A mailbox (email account) owned by a user or organization. No credentials are ever exposed. */
export interface AdminMailbox {
  id: string;
  email: string;
  displayName: string;
  /** Hex color used for the small swatch (the only place a raw hex is allowed in the UI). */
  color: string;
  active: boolean;
  createdAt: string;
}

/** A condensed automation row for the admin Organizations detail → Automations tab. */
export interface AdminAutomationSummary {
  id: string;
  name: string;
  status: string;
  kind: string;
  createdAt: string;
  lastRunAt: string | null;
}

export interface PlanModel {
  id: string;
  name: string;
  tokenLimit: number;
  automationLimit: number;
  emailAccountLimit: number;
  price: number;
  costLimitCents: number;
  /** Whether the outbound API/webhook feature is enabled for this plan. */
  apiWebhookEnabled: boolean;
  /** Max active inbound webhook endpoints: -1 = unlimited, 0 = none, >0 = cap. */
  inboundWebhookLimit: number;
  /** Whether subscribers on this plan may publish to the marketplace. */
  marketplacePublishEnabled: boolean;
  /** Derived: true for the platform default plan (name === STARTER). */
  isDefault: boolean;
  userCount: number;
  createdAt: string;
}

export interface PlanRequest {
  name: string;
  tokenLimit: number;
  automationLimit: number;
  emailAccountLimit: number;
  price: number;
  costLimitCents: number;
  /** Whether the outbound API/webhook feature is enabled for this plan. */
  apiWebhookEnabled: boolean;
  /** Max active inbound webhook endpoints: -1 = unlimited, 0 = none, >0 = cap. */
  inboundWebhookLimit: number;
  /** Optional: omitted → preserved on update / defaults true on create. */
  marketplacePublishEnabled?: boolean;
}

/** An editable per-model AI pricing row (USD per million tokens). */
export interface ModelPricing {
  id: string;
  model: string;
  inputPerMillion: number;
  outputPerMillion: number;
  updatedAt: string;
}

export interface ModelPricingRequest {
  model: string;
  inputPerMillion: number;
  outputPerMillion: number;
}

/** Current staff caller's identity + capabilities (GET /admin/me). Drives nav + action gating. */
export interface StaffIdentity {
  email: string;
  role: string;
  /** Null when the user is not platform staff. */
  staffRole: string | null;
  /** Discrete StaffPermission names granted by the staff role. */
  permissions: string[];
}
