import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { UsageResponse } from '../../models/usage.model';

/** Micros in one cent — AI cost is tracked in micros (1 cent = 10,000 micros). */
const MICROS_PER_CENT = 10_000;

export interface PlanSummary {
  id: string;
  name: string;
  tokenLimit: number;
  automationLimit: number;
  emailAccountLimit: number;
  price: number;
  apiWebhookEnabled: boolean;
  costLimitCents: number;
}

/** Retrieves subscription plan listings and the current user's usage metrics. */
@Injectable({ providedIn: 'root' })
export class PlanService {
  private readonly api = inject(ApiService);

  /** Cached usage data shared across topbar, chat panel, analytics, and plans page. */
  readonly usage = signal<UsageResponse | null>(null);

  /** True while a usage refresh is in flight (drives the topbar manual-refresh spinner). */
  readonly loadingUsage = signal(false);

  /** AI cost unlimited (Enterprise, cap = -1) — render ∞ instead of a percentage. */
  readonly costUnlimited = computed(() => this.usage()?.plan.costLimitCents === -1);

  /**
   * Monthly AI cost as an UNROUNDED percentage of the plan's cost cap. `null` when there is no
   * finite cap (unlimited -1 or AI disabled 0) or usage hasn't loaded — callers render ∞ / — / —.
   * Single source of truth for the AI-usage percentage (topbar limiter + analytics).
   */
  readonly costUsagePercent = computed<number | null>(() => {
    const u = this.usage();
    if (!u) return null;
    const cap = u.plan.costLimitCents;
    if (!cap || cap <= 0) return null; // unlimited (-1) or disabled (0) → no percentage
    const micros = u.usage.costUsedMicros ?? u.usage.costUsedCents * MICROS_PER_CENT;
    return (micros / (cap * MICROS_PER_CENT)) * 100;
  });

  getPlans(): Observable<PlanSummary[]> {
    return this.api.get<PlanSummary[]>('/users/plans');
  }

  getUsage(): Observable<UsageResponse> {
    return this.api.get<UsageResponse>('/users/me/usage');
  }

  /** Fetch usage and cache in signal. Call from components instead of subscribing individually. */
  loadUsage(): void {
    this.loadingUsage.set(true);
    this.api.get<UsageResponse>('/users/me/usage').subscribe({
      next: (data) => { this.usage.set(data); this.loadingUsage.set(false); },
      error: () => this.loadingUsage.set(false),
    });
  }
}
