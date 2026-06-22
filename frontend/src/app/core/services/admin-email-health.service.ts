import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { pageParams } from '../../shared/utils/page-params.util';
import {
  MailboxHealthRow,
  MailboxHealthDetail,
  EmailHealthKpis,
  EmailClusterSummary,
  EmailHealthFilters,
  Page,
} from '../../models/admin-email-health.model';

/**
 * Platform-staff API client for Email Health: cross-tenant mailbox list (filterable + paginated),
 * KPI totals, by-cluster summaries, per-mailbox detail, and re-sync / pause / resume mutations.
 * Reads need INFRA_VIEW; mutations need INFRA_MANAGE (enforced + audit-logged on the backend).
 */
@Injectable({ providedIn: 'root' })
export class AdminEmailHealthService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/email-health';

  listMailboxes(filters: EmailHealthFilters = {}, page = 0, size = 10) {
    const params = pageParams(filters, page, size);
    return this.api.get<Page<MailboxHealthRow>>(`${this.base}/mailboxes`, { params });
  }

  kpis() {
    return this.api.get<EmailHealthKpis>(`${this.base}/kpis`);
  }

  clusters() {
    return this.api.get<EmailClusterSummary[]>(`${this.base}/clusters`);
  }

  getMailbox(id: string) {
    return this.api.get<MailboxHealthDetail>(`${this.base}/mailboxes/${id}`);
  }

  resync(id: string) {
    return this.api.post<MailboxHealthRow>(`${this.base}/mailboxes/${id}/resync`, {});
  }

  pause(id: string) {
    return this.api.post<MailboxHealthDetail>(`${this.base}/mailboxes/${id}/pause`, {});
  }

  resume(id: string) {
    return this.api.post<MailboxHealthDetail>(`${this.base}/mailboxes/${id}/resume`, {});
  }
}
