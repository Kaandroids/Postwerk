import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { ApprovalStatus, PendingAction, PendingActionPage } from '../../models/pending-action.model';

/**
 * Supervised-mode approval inbox (#3a): list / approve / reject actions parked for human review,
 * and the pending count for the nav badge.
 */
@Injectable({ providedIn: 'root' })
export class PendingActionService {
  private readonly api = inject(ApiService);
  private readonly basePath = '/pending-actions';

  list(status?: ApprovalStatus) {
    return this.api.get<PendingActionPage>(`${this.basePath}${status ? `?status=${status}` : ''}`);
  }

  count() {
    return this.api.get<{ pending: number }>(`${this.basePath}/count`);
  }

  approve(id: string) {
    return this.api.post<PendingAction>(`${this.basePath}/${id}/approve`, {});
  }

  reject(id: string, note?: string) {
    const q = note ? `?note=${encodeURIComponent(note)}` : '';
    return this.api.post<PendingAction>(`${this.basePath}/${id}/reject${q}`, {});
  }

  /** #3c: teach the correct category from this action's email and reject the wrongly-triggered action. */
  reclassify(id: string, categoryId: string) {
    return this.api.post<PendingAction>(`${this.basePath}/${id}/reclassify?categoryId=${categoryId}`, {});
  }
}
