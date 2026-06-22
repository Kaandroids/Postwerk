import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { pageParams } from '../../shared/utils/page-params.util';
import {
  Announcement,
  AnnouncementDetail,
  AnnouncementKpis,
  AnnouncementFilters,
  AnnouncementRequest,
  Page,
} from '../../models/admin-announcement.model';

/**
 * Platform-staff API client for the Announcements console: the announcement queue (filterable +
 * paginated), lifecycle KPIs, per-record detail (+ change history) and the gated mutations
 * (create / save / publish / end / archive / duplicate). All gate on ANNOUNCEMENT_MANAGE + audit-logged.
 */
@Injectable({ providedIn: 'root' })
export class AdminAnnouncementService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/announcements';

  list(filters: AnnouncementFilters = {}, page = 0, size = 10) {
    const params = pageParams(filters, page, size);
    return this.api.get<Page<Announcement>>(this.base, { params });
  }

  kpis() { return this.api.get<AnnouncementKpis>(`${this.base}/kpis`); }
  get(id: string) { return this.api.get<AnnouncementDetail>(`${this.base}/${id}`); }

  create(body: AnnouncementRequest) { return this.api.post<Announcement>(this.base, body); }
  update(id: string, body: AnnouncementRequest) { return this.api.put<Announcement>(`${this.base}/${id}`, body); }
  publish(id: string) { return this.api.post<Announcement>(`${this.base}/${id}/publish`, {}); }
  end(id: string) { return this.api.post<Announcement>(`${this.base}/${id}/end`, {}); }
  archive(id: string) { return this.api.post<Announcement>(`${this.base}/${id}/archive`, {}); }
  duplicate(id: string) { return this.api.post<Announcement>(`${this.base}/${id}/duplicate`, {}); }
}
