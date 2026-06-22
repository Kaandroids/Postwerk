import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, of, shareReplay, tap } from 'rxjs';
import { ApiService } from './api.service';
import { StaffIdentity } from '../../models/admin.model';

/**
 * Loads and caches the current staff caller's identity (GET /api/v1/admin/me) once per session.
 *
 * <p>"Is staff" is the authoritative gate for the admin console (server-derived `staffRole`), replacing
 * the old `role === 'ADMIN'` JWT check so SUPPORT/BILLING/etc. (whose platform role is USER) can reach
 * the panel. Action-level UI gating uses {@link has} against the role's discrete permissions. Client
 * gating is UX only — every capability is also enforced on the backend.</p>
 */
@Injectable({ providedIn: 'root' })
export class AdminIdentityService {
  private readonly api = inject(ApiService);

  private readonly _identity = signal<StaffIdentity | null>(null);
  private request$: Observable<StaffIdentity> | null = null;

  readonly identity = this._identity.asReadonly();
  readonly isStaff = computed(() => !!this._identity()?.staffRole);
  readonly staffRole = computed(() => this._identity()?.staffRole ?? null);
  private readonly permSet = computed(() => new Set(this._identity()?.permissions ?? []));

  /** Loads /admin/me once, caching the result. Concurrent callers share one in-flight request. */
  load(): Observable<StaffIdentity> {
    const current = this._identity();
    if (current) return of(current);
    if (!this.request$) {
      this.request$ = this.api.get<StaffIdentity>('/admin/me').pipe(
        tap({
          next: (id) => this._identity.set(id),
          error: () => { this.request$ = null; }, // allow retry after a transient failure
        }),
        shareReplay(1),
      );
    }
    return this.request$;
  }

  /** Whether the current staff role grants the given StaffPermission. */
  has(permission: string): boolean {
    return this.permSet().has(permission);
  }

  /** Clears the cached identity (call on logout / role change). */
  clear(): void {
    this._identity.set(null);
    this.request$ = null;
  }
}
