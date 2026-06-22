import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { ActivityPage } from '../../models/activity.model';

/** Production activity feed (#3d): recent live automation runs with per-step results + AI reasoning. */
@Injectable({ providedIn: 'root' })
export class ActivityService {
  private readonly api = inject(ApiService);

  recent(page = 0) {
    return this.api.get<ActivityPage>(`/activity?page=${page}`);
  }
}
