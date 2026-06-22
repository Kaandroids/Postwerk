import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { pageParams } from '../../shared/utils/page-params.util';
import {
  QuotaOverride,
  QuotaKpis,
  QuotaOverrideRequest,
  QuotaOverrideFilters,
  Page,
} from '../../models/admin-quota.model';

/**
 * Platform-staff API client for per-user / per-org AI quota overrides:
 * listing (filterable + paginated), KPI totals, and create / update / revoke mutations.
 * Every mutation is also enforced (and audit-logged) on the backend.
 */
@Injectable({ providedIn: 'root' })
export class AdminQuotaService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/quota-overrides';

  list(filters: QuotaOverrideFilters = {}, page = 0, size = 10) {
    const params = pageParams(filters, page, size);
    return this.api.get<Page<QuotaOverride>>(this.base, { params });
  }

  kpis() {
    return this.api.get<QuotaKpis>(`${this.base}/kpis`);
  }

  create(req: QuotaOverrideRequest) {
    return this.api.post<QuotaOverride>(this.base, req);
  }

  update(id: string, req: QuotaOverrideRequest) {
    return this.api.put<QuotaOverride>(`${this.base}/${id}`, req);
  }

  revoke(id: string) {
    return this.api.delete<void>(`${this.base}/${id}`);
  }
}
