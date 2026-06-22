import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { pageParams } from '../../shared/utils/page-params.util';
import {
  Subscription,
  SubscriptionDetail,
  SubscriptionKpis,
  SubscriptionFilters,
  PlanHistoryEntry,
  Page,
} from '../../models/admin-subscription.model';

/**
 * Platform-staff API client for Plans & Subscriptions: the subscriptions list (filterable +
 * paginated), KPI totals, per-org detail, plan-change, and plan-history. Reads need PLAN_VIEW/
 * BILLING_VIEW; plan changes need PLAN_MANAGE (enforced + audit-logged on the backend).
 * (Plan catalog CRUD is the existing AdminService; grant-credit is AdminQuotaService.)
 */
@Injectable({ providedIn: 'root' })
export class AdminSubscriptionService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/subscriptions';

  list(filters: SubscriptionFilters = {}, page = 0, size = 10) {
    const params = pageParams(filters, page, size);
    return this.api.get<Page<Subscription>>(this.base, { params });
  }

  kpis() {
    return this.api.get<SubscriptionKpis>(`${this.base}/kpis`);
  }

  get(orgId: string) {
    return this.api.get<SubscriptionDetail>(`${this.base}/${orgId}`);
  }

  planHistory(orgId: string) {
    return this.api.get<PlanHistoryEntry[]>(`${this.base}/${orgId}/plan-history`);
  }

  changePlan(orgId: string, planId: string, reason: string | null) {
    return this.api.patch<SubscriptionDetail>(`${this.base}/${orgId}/plan`, { planId, reason });
  }
}
