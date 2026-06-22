import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TokenService } from './token.service';

interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  role: string;
}

/**
 * Single source of truth for refreshing the access token. Shared by the auth guard
 * and the HTTP interceptor so that concurrent refreshes within a tab are de-duplicated
 * (single-flight) and cross-tab refresh-token rotation does not spuriously log the
 * user out.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);
  private readonly tokenService = inject(TokenService);

  /** The in-progress refresh, shared by all callers until it settles. */
  private inFlight: Promise<boolean> | null = null;

  /**
   * Attempts to obtain a fresh access token using the stored refresh token.
   * Returns true if the session is (now) valid, false if the user must re-authenticate.
   * Does NOT clear tokens or navigate — the caller decides what to do when it returns false.
   */
  refreshSession(): Promise<boolean> {
    if (this.inFlight) return this.inFlight;

    const refreshToken = this.tokenService.getRefreshToken();
    if (!refreshToken) return Promise.resolve(false);

    this.inFlight = this.doRefresh(refreshToken).finally(() => {
      this.inFlight = null;
    });
    return this.inFlight;
  }

  private async doRefresh(refreshToken: string): Promise<boolean> {
    try {
      const res = await firstValueFrom(
        this.http.post<RefreshResponse>(`${environment.apiUrl}/auth/refresh`, { refreshToken }),
      );
      this.tokenService.saveTokens(res.accessToken, res.refreshToken, res.role);
      return true;
    } catch {
      // Cross-tab rotation: another tab may have already refreshed (and rotated) the
      // refresh token while our request was in flight, which invalidates ours. If storage
      // now holds a different token and the access token is valid, the session is fine —
      // recover instead of logging the user out.
      const current = this.tokenService.getRefreshToken();
      if (current && current !== refreshToken && this.tokenService.isLoggedIn()) {
        return true;
      }
      return false;
    }
  }
}
