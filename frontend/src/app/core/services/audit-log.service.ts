import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { ApiService } from './api.service';
import { AuditLogPage } from '../../models/audit-log.model';

/**
 * Retrieves paginated audit log entries from the backend, with optional filtering by action type.
 */
@Injectable({ providedIn: 'root' })
export class AuditLogService {
  private api = inject(ApiService);

  list(page: number = 0, size: number = 20, action?: string) {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (action) {
      params = params.set('action', action);
    }

    return this.api.get<AuditLogPage>('/audit-logs', { params });
  }
}
