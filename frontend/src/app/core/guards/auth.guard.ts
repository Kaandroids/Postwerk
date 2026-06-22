import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenService } from '../services/token.service';
import { SessionService } from '../services/session.service';

/**
 * Route guard that allows access when the user has a usable session. If the access
 * token has expired but a refresh token is still present, it transparently refreshes
 * before deciding — so an idle user is not bounced to login on reload/navigation.
 * Redirects to login only when no valid (or refreshable) session exists.
 */
export const authGuard: CanActivateFn = async () => {
  const tokenService = inject(TokenService);
  const sessionService = inject(SessionService);
  const router = inject(Router);

  if (tokenService.isLoggedIn()) return true;

  // Access token missing/expired — try a transparent refresh before giving up.
  if (await sessionService.refreshSession()) return true;

  tokenService.clearTokens();
  router.navigate(['/auth/login']);
  return false;
};
