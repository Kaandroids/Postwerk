import { Injectable } from '@angular/core';

const ACCESS_TOKEN_KEY = 'access_token';
const REFRESH_TOKEN_KEY = 'refresh_token';
const USER_ROLE_KEY = 'user_role';

/**
 * Handles JWT access and refresh token persistence in localStorage.
 *
 * Provides methods to save, retrieve, clear tokens and check authentication status.
 */
@Injectable({ providedIn: 'root' })
export class TokenService {
  saveTokens(accessToken: string, refreshToken: string, role?: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    if (role) {
      localStorage.setItem(USER_ROLE_KEY, role);
    }
  }

  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  clearTokens(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_ROLE_KEY);
  }

  getRole(): string {
    return localStorage.getItem(USER_ROLE_KEY) ?? 'USER';
  }

  isAdmin(): boolean {
    return this.getRole() === 'ADMIN';
  }

  isLoggedIn(): boolean {
    const token = this.getAccessToken();
    if (!token) return false;
    return !this.isTokenExpired(token);
  }

  private isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (!payload.exp) return false;
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  }
}
