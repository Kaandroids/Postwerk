import { Page } from './page.model';

export type { Page };

/** Subscription lifecycle status. */
export type SubscriptionStatus = 'active' | 'suspended';

/** One organization's subscription row (admin Plans & Subscriptions list). */
export interface Subscription {
  orgId: string;
  orgName: string;
  slug: string;
  personal: boolean;
  ownerName: string | null;
  ownerEmail: string | null;
  planName: string | null;
  status: SubscriptionStatus;
  memberCount: number;
  mailboxCount: number;
  automationCount: number;
  aiCostMicrosThisMonth: number;
  /** Effective AI cap in cents after overrides: -1 = unlimited, 0 = AI off, >0 = monthly cap. */
  effectiveCapCents: number;
  createdAt: string;
}

/** One plan-assignment change in the history timeline. */
export interface PlanHistoryEntry {
  at: string;
  detail: string;
  by: string | null;
}

/** Full subscription detail for the detail modal. */
export interface SubscriptionDetail {
  orgId: string;
  orgName: string;
  slug: string;
  personal: boolean;
  ownerName: string | null;
  ownerEmail: string | null;
  status: SubscriptionStatus;
  planId: string | null;
  planName: string | null;
  planPrice: number | null;
  planCostLimitCents: number;
  effectiveCapCents: number;
  aiCostMicrosThisMonth: number;
  memberCount: number;
  mailboxCount: number;
  automationCount: number;
  mailboxLimit: number;
  automationLimit: number;
  createdAt: string;
  suspendedAt: string | null;
  planHistory: PlanHistoryEntry[];
}

/** KPI strip totals (GET /admin/subscriptions/kpis). */
export interface SubscriptionKpis {
  mrr: number;
  activeSubscriptions: number;
  aiCostCentsThisMonth: number;
  overCapCount: number;
  planCount: number;
}

/** Filters passed to the list endpoint (all optional; empty = "All"). */
export interface SubscriptionFilters {
  search?: string;
  plan?: string;
  status?: '' | SubscriptionStatus;
  usage?: '' | 'over90' | 'unlimited' | 'aiOff';
}
