import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { TokenService } from '../services/token.service';
import { AdminIdentityService } from '../services/admin-identity.service';

/**
 * Guards the platform-staff admin area. Authorization is "is staff" (server-derived `staffRole` via
 * GET /admin/me), NOT the coarse `role === 'ADMIN'` JWT claim — so SUPPORT/BILLING/etc. (platform
 * role USER) can reach the panel while non-staff users are redirected. The backend independently
 * enforces ROLE_STAFF + per-endpoint permissions.
 */
export const adminGuard: CanActivateFn = (route) => {
  const tokenService = inject(TokenService);
  const identity = inject(AdminIdentityService);
  const router = inject(Router);

  if (!tokenService.isLoggedIn()) {
    router.navigate(['/auth/login']);
    return of(false);
  }

  // Optional per-route capability: a staffer without it is bounced to the admin overview (every
  // staff role carries PLATFORM_DASHBOARD_VIEW) instead of landing on a backend-403'd broken page.
  // The backend still independently enforces the capability on every endpoint.
  const perm = route.data?.['perm'] as string | undefined;

  return identity.load().pipe(
    map((id) => {
      if (!id?.staffRole) { router.navigate(['/dashboard']); return false; }
      if (perm && !identity.has(perm)) { router.navigate(['/dashboard/admin']); return false; }
      return true;
    }),
    catchError(() => {
      router.navigate(['/dashboard']);
      return of(false);
    }),
  );
};
