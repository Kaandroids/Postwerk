import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenService } from '../services/token.service';

/**
 * Route guard that redirects already-authenticated users to the dashboard.
 * Used on auth pages (login, register) to prevent logged-in users from seeing them.
 * Allows the wizard claim flow through (query param `from=wizard`).
 */
export const guestGuard: CanActivateFn = (route) => {
  const tokenService = inject(TokenService);
  const router = inject(Router);

  if (tokenService.isLoggedIn()) {
    // Allow wizard claim flow — the register page handles session claiming
    if (route.queryParamMap.get('from') === 'wizard') {
      return true;
    }
    router.navigate(['/dashboard']);
    return false;
  }

  return true;
};
