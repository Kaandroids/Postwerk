import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { ApiService } from './api.service';
import { AdminOrg, AdminOrgDetail, Page } from '../../models/admin-org.model';
import { AdminAutomationSummary, AdminMailbox } from '../../models/admin.model';

/**
 * Platform-staff API client for managing organizations (tenants):
 * listing, detail lookup, suspend/activate, ownership transfer and deletion.
 */
@Injectable({ providedIn: 'root' })
export class AdminOrganizationService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/organizations';

  list(search = '', personal?: boolean, page = 0, size = 20) {
    let params = new HttpParams()
      .set('search', search)
      .set('page', page)
      .set('size', size);
    if (personal !== undefined) {
      params = params.set('personal', personal);
    }
    return this.api.get<Page<AdminOrg>>(this.base, { params });
  }

  get(id: string) {
    return this.api.get<AdminOrgDetail>(`${this.base}/${id}`);
  }

  /** Automations owned by the org (capped at 200, newest first — no pagination). */
  getAutomations(id: string) {
    return this.api.get<AdminAutomationSummary[]>(`${this.base}/${id}/automations`);
  }

  /** Mailboxes (email accounts) owned by the org. No credentials are exposed. */
  getMailboxes(id: string) {
    return this.api.get<AdminMailbox[]>(`${this.base}/${id}/mailboxes`);
  }

  suspend(id: string, reason?: string) {
    return this.api.post<AdminOrgDetail>(`${this.base}/${id}/suspend`, { reason: reason ?? null });
  }

  activate(id: string) {
    return this.api.post<AdminOrgDetail>(`${this.base}/${id}/activate`, {});
  }

  transferOwnership(id: string, newOwnerUserId: string) {
    return this.api.post<AdminOrgDetail>(`${this.base}/${id}/transfer-ownership`, { newOwnerUserId });
  }

  remove(id: string) {
    return this.api.delete<void>(`${this.base}/${id}`);
  }
}
