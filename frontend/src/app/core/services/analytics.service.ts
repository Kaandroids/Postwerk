import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { AnalyticsOverview, AutomationAnalyticsDetail, AnalyticsRange } from '../../models/analytics.model';

/**
 * Analytics (#analytics): org-scoped automation performance + AI cost. Backed by
 * `/api/v1/analytics/*`; the active organization is applied server-side from the X-Org-Id header.
 */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly api = inject(ApiService);

  overview(range: AnalyticsRange) {
    return this.api.get<AnalyticsOverview>(`/analytics/overview?range=${range}`);
  }

  automationDetail(id: string, range: AnalyticsRange) {
    return this.api.get<AutomationAnalyticsDetail>(`/analytics/automations/${id}?range=${range}`);
  }
}
