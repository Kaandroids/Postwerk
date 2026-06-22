import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import {
  Job,
  JobDetail,
  JobQueue,
  BackgroundJobsKpis,
} from '../../models/admin-background-jobs.model';

/**
 * Platform-staff API client for Background Jobs: the recurring-job registry (with run history),
 * KPIs, work-queue summaries, per-job detail, and run-now / pause / resume mutations. Reads need
 * INFRA_VIEW; mutations need INFRA_MANAGE (enforced + audit-logged on the backend).
 */
@Injectable({ providedIn: 'root' })
export class AdminBackgroundJobsService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/background-jobs';

  jobs() {
    return this.api.get<Job[]>(`${this.base}/jobs`);
  }

  kpis() {
    return this.api.get<BackgroundJobsKpis>(`${this.base}/kpis`);
  }

  queues() {
    return this.api.get<JobQueue[]>(`${this.base}/queues`);
  }

  getJob(id: string) {
    return this.api.get<JobDetail>(`${this.base}/jobs/${id}`);
  }

  runNow(id: string) {
    return this.api.post<Job>(`${this.base}/jobs/${id}/run`, {});
  }

  pause(id: string) {
    return this.api.post<Job>(`${this.base}/jobs/${id}/pause`, {});
  }

  resume(id: string) {
    return this.api.post<Job>(`${this.base}/jobs/${id}/resume`, {});
  }
}
