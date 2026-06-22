import { HttpInterceptorFn, HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { TokenService } from '../services/token.service';
import { SessionService } from '../services/session.service';
import { OrganizationService } from '../services/organization.service';
import { QuotaNotificationService } from '../services/quota-notification.service';

/**
 * HTTP interceptor that attaches the JWT Bearer token (and active org) to outgoing
 * requests and transparently refreshes an expired access token on a 401, retrying the
 * request once. Concurrent 401s share a single refresh via SessionService (single-flight),
 * so no request hangs waiting on a failed refresh. Auth endpoints are excluded from both
 * token attachment and refresh handling.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenService = inject(TokenService);
  const sessionService = inject(SessionService);
  const orgService = inject(OrganizationService);
  const router = inject(Router);
  const quotaNotification = inject(QuotaNotificationService);

  const isAuthCall = req.url.includes('/auth/');
  const orgId = orgService.activeOrgId();

  // Re-read the access token on every (re)try so a freshly refreshed token is picked up.
  const withAuth = (request: HttpRequest<unknown>): HttpRequest<unknown> => {
    if (isAuthCall) return request;
    const setHeaders: Record<string, string> = {};
    const token = tokenService.getAccessToken();
    if (token) setHeaders['Authorization'] = `Bearer ${token}`;
    if (orgId) setHeaders['X-Org-Id'] = orgId; // active tenant (multi-tenant #4)
    return Object.keys(setHeaders).length ? request.clone({ setHeaders }) : request;
  };

  return next(withAuth(req)).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 429 && error.error?.limitType) {
        quotaNotification.show(error.error);
      }
      if (error.status === 401 && !isAuthCall) {
        return from(sessionService.refreshSession()).pipe(
          switchMap((refreshed) => {
            if (refreshed) {
              return next(withAuth(req)); // retry once with the new token
            }
            tokenService.clearTokens();
            router.navigate(['/auth/login']);
            return throwError(() => error);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
