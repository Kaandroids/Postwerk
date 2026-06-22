import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { pageParams } from '../../shared/utils/page-params.util';
import {
  GdprRequest,
  GdprRequestDetail,
  GdprKpis,
  GdprRetention,
  GdprFilters,
  CreateGdprRequest,
  Page,
} from '../../models/admin-gdpr.model';

/**
 * Platform-staff API client for GDPR / Data Requests (DSARs): the request queue (filterable +
 * paginated), KPI roll-up, the automated retention posture, per-request detail (footprint +
 * timeline), and the moderation mutations (create / run export / execute erasure / reject /
 * mark complete). Read with COMPLIANCE_VIEW; mutations gated COMPLIANCE_MANAGE + audit-logged
 * on the backend. Erasure is irreversible.
 */
@Injectable({ providedIn: 'root' })
export class AdminGdprService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/gdpr';

  requests(filters: GdprFilters = {}, page = 0, size = 10) {
    const params = pageParams(filters, page, size);
    return this.api.get<Page<GdprRequest>>(`${this.base}/requests`, { params });
  }

  kpis() { return this.api.get<GdprKpis>(`${this.base}/kpis`); }
  retention() { return this.api.get<GdprRetention>(`${this.base}/retention`); }
  getRequest(id: string) { return this.api.get<GdprRequestDetail>(`${this.base}/requests/${id}`); }

  create(body: CreateGdprRequest) { return this.api.post<GdprRequest>(`${this.base}/requests`, body); }
  runExport(id: string) { return this.api.post<GdprRequest>(`${this.base}/requests/${id}/export`, {}); }
  executeErasure(id: string) { return this.api.post<GdprRequest>(`${this.base}/requests/${id}/erase`, {}); }
  reject(id: string, reason: string) { return this.api.post<GdprRequest>(`${this.base}/requests/${id}/reject`, { reason }); }
  markComplete(id: string) { return this.api.post<GdprRequest>(`${this.base}/requests/${id}/complete`, {}); }
}
