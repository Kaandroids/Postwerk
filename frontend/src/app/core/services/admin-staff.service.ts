import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { ApiService } from './api.service';
import { pageParams } from '../../shared/utils/page-params.util';
import {
  StaffMember,
  StaffKpis,
  StaffRolesMatrix,
  StaffCandidate,
  StaffFilters,
  Page,
} from '../../models/admin-staff.model';

/**
 * Platform-staff API client for the Staff & Roles console: the staff roster (filterable + paginated),
 * role KPIs, the read-only role→capability matrix, grant-access candidates, and grant / change-role /
 * revoke mutations. All gate on STAFF_MANAGE (Super Admin only); self-change is rejected + audit-logged.
 */
@Injectable({ providedIn: 'root' })
export class AdminStaffService {
  private readonly api = inject(ApiService);
  private readonly base = '/admin/staff';

  roster(filters: StaffFilters = {}, page = 0, size = 10) {
    const params = pageParams(filters, page, size);
    return this.api.get<Page<StaffMember>>(this.base, { params });
  }

  kpis() { return this.api.get<StaffKpis>(`${this.base}/kpis`); }
  roles() { return this.api.get<StaffRolesMatrix>(`${this.base}/roles`); }
  candidates(search: string) {
    const params = new HttpParams().set('search', search);
    return this.api.get<StaffCandidate[]>(`${this.base}/candidates`, { params });
  }

  /** Grant or change a staff member's role. */
  setRole(userId: string, role: string) { return this.api.post<StaffMember>(`${this.base}/${userId}`, { role }); }
  /** Revoke all staff access. */
  revoke(userId: string) { return this.api.delete<StaffMember>(`${this.base}/${userId}`); }
}
