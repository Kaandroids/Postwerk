import { Page } from './page.model';

export type { Page };

/** Target kind of a quota override — a user or an organization (tenant). */
export type QuotaTargetType = 'USER' | 'ORG';

/** Override flavour: add one-off credit, replace the monthly cap, or remove the cap entirely. */
export type QuotaOverrideKind = 'CREDIT' | 'CAP' | 'UNLIMITED';

/** Lifecycle status (the backend never returns "scheduled"). */
export type QuotaOverrideStatus = 'active' | 'expired';

/** A single per-user / per-org AI quota override as returned by the admin API. */
export interface QuotaOverride {
  id: string;
  targetType: QuotaTargetType;
  targetId: string;
  targetName: string;
  /** User email OR org slug. */
  targetEmailOrSlug: string;
  basePlan: string;
  kind: QuotaOverrideKind;
  /** Credit/cap amount in cents (null for UNLIMITED). */
  amountCents: number | null;
  /** The plan's base monthly cap in cents. */
  baseCapCents: number;
  /** Resulting monthly cap in cents; null = unlimited. */
  effectiveCapCents: number | null;
  /** Spend so far this period, in cents (drives the mini-bar). */
  currentSpendCents: number;
  expiresAt: string | null;
  reason: string;
  grantedByName: string;
  createdAt: string;
  status: QuotaOverrideStatus;
}

/** KPI strip totals (GET /admin/quota-overrides/kpis). */
export interface QuotaKpis {
  activeCount: number;
  creditGrantedThisMonthCents: number;
  over80Count: number;
  expiringIn7Count: number;
}

/** Create / update payload. Target is locked on update (server ignores a changed target). */
export interface QuotaOverrideRequest {
  targetType: QuotaTargetType;
  targetId: string;
  kind: QuotaOverrideKind;
  /** > 0 for CREDIT/CAP; null for UNLIMITED. */
  amountCents: number | null;
  expiresAt: string | null;
  reason: string;
}

/** Filters passed to the list endpoint (all optional; empty = "All"). */
export interface QuotaOverrideFilters {
  search?: string;
  targetType?: '' | QuotaTargetType;
  kind?: '' | QuotaOverrideKind;
  status?: '' | QuotaOverrideStatus;
  expiry?: '' | 'next7' | 'next30';
}
