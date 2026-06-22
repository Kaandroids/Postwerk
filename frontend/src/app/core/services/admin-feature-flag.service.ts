import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { pageParams } from '../../shared/utils/page-params.util';
import {
  FeatureFlag,
  FeatureFlagDetail,
  FeatureFlagKpis,
  FlagFilters,
  CreateFlagRequest,
  UpdateFlagRequest,
  Page,
} from '../../models/admin-feature-flag.model';

/**
 * Platform-staff API client for the Feature Flags console: the flag list (filterable + paginated),
 * definition/state/staleness KPIs, per-flag detail (+ change history) and the gated mutations
 * (create / save / enable / disable / kill / restore / archive / duplicate). All gate on
 * FEATURE_FLAG_MANAGE + audit-logged.
 */
@Injectable({ providedIn: 'root' })
export class AdminFeatureFlagService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/feature-flags';

  list(filters: FlagFilters = {}, page = 0, size = 10) {
    const params = pageParams(filters, page, size);
    return this.api.get<Page<FeatureFlag>>(this.base, { params });
  }

  kpis() { return this.api.get<FeatureFlagKpis>(`${this.base}/kpis`); }
  get(id: string) { return this.api.get<FeatureFlagDetail>(`${this.base}/${id}`); }

  create(body: CreateFlagRequest) { return this.api.post<FeatureFlag>(this.base, body); }
  update(id: string, body: UpdateFlagRequest) { return this.api.put<FeatureFlag>(`${this.base}/${id}`, body); }
  enable(id: string) { return this.api.post<FeatureFlag>(`${this.base}/${id}/enable`, {}); }
  disable(id: string) { return this.api.post<FeatureFlag>(`${this.base}/${id}/disable`, {}); }
  kill(id: string) { return this.api.post<FeatureFlag>(`${this.base}/${id}/kill`, {}); }
  restore(id: string) { return this.api.post<FeatureFlag>(`${this.base}/${id}/restore`, {}); }
  archive(id: string) { return this.api.post<FeatureFlag>(`${this.base}/${id}/archive`, {}); }
  duplicate(id: string) { return this.api.post<FeatureFlag>(`${this.base}/${id}/duplicate`, {}); }
}
