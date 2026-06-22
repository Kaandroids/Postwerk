import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import {
  Subsystem,
  SystemHealthKpis,
  SystemHealthEvent,
  MaintenanceMode,
} from '../../models/admin-system-health.model';

/**
 * Platform-staff API client for System Health: live subsystem probes, KPI totals, recent-events feed,
 * per-subsystem detail/re-probe, cache flush, and the maintenance-mode toggle. Reads need INFRA_VIEW;
 * mutations need INFRA_MANAGE (enforced + audit-logged on the backend).
 */
@Injectable({ providedIn: 'root' })
export class AdminSystemHealthService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/system-health';

  subsystems() {
    return this.api.get<Subsystem[]>(`${this.base}/subsystems`);
  }

  kpis() {
    return this.api.get<SystemHealthKpis>(`${this.base}/kpis`);
  }

  events() {
    return this.api.get<SystemHealthEvent[]>(`${this.base}/events`);
  }

  getSubsystem(id: string) {
    return this.api.get<Subsystem>(`${this.base}/subsystems/${id}`);
  }

  probe(id: string) {
    return this.api.post<Subsystem>(`${this.base}/subsystems/${id}/probe`, {});
  }

  flushCache() {
    return this.api.post<void>(`${this.base}/cache/flush`, {});
  }

  getMaintenance() {
    return this.api.get<MaintenanceMode>(`${this.base}/maintenance`);
  }

  setMaintenance(enabled: boolean, message: string | null) {
    return this.api.put<MaintenanceMode>(`${this.base}/maintenance`, { enabled, message });
  }
}
