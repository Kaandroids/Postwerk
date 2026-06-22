import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { TokenService } from '../../../core/services/token.service';
import { humanizeError } from '../../../shared/utils/error.util';

interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  role: string;
}

interface RegisterResponse {
  verificationRequired: boolean;
  email: string;
}

interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  company?: string;
  phone?: string;
  marketingOptIn: boolean;
  termsAccepted: boolean;
  lang?: string;
  wizardSessionId?: string;
}

/** Result of a login attempt. `needsVerification` flags an unverified account (HTTP 403, EMAIL_NOT_VERIFIED). */
interface LoginResult {
  success: boolean;
  error?: string;
  needsVerification?: boolean;
  email?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenService = inject(TokenService);
  private readonly authUrl = `${environment.apiUrl}/auth`;

  isValidEmail(email: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
  }

  async login(email: string, password: string): Promise<LoginResult> {
    try {
      const response = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.authUrl}/login`, { email, password })
      );
      this.tokenService.saveTokens(response.accessToken, response.refreshToken, response.role);
      return { success: true };
    } catch (err: any) {
      if (err?.error?.code === 'EMAIL_NOT_VERIFIED') {
        return { success: false, needsVerification: true, email: err.error.email ?? email };
      }
      return { success: false, error: humanizeError(err, 'Login failed') };
    }
  }

  /** Registers a new account. No tokens are issued — the user must verify their email first. */
  async register(request: RegisterRequest): Promise<{ success: boolean; email?: string; error?: string }> {
    try {
      const response = await firstValueFrom(
        this.http.post<RegisterResponse>(`${this.authUrl}/register`, request)
      );
      return { success: true, email: response.email };
    } catch (err: any) {
      return { success: false, error: humanizeError(err, 'Registration failed') };
    }
  }

  /** Confirms an email from a verification token and logs the user in (tokens saved on success). */
  async verifyEmail(token: string): Promise<{ success: boolean; error?: string }> {
    try {
      const response = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.authUrl}/verify-email`, { token })
      );
      this.tokenService.saveTokens(response.accessToken, response.refreshToken, response.role);
      return { success: true };
    } catch (err: any) {
      return { success: false, error: humanizeError(err, 'Verification failed') };
    }
  }

  /** Re-sends the verification email. Always resolves successfully (no account-existence leak). */
  async resendVerification(email: string, lang?: string): Promise<{ success: boolean; error?: string }> {
    try {
      await firstValueFrom(
        this.http.post(`${this.authUrl}/resend-verification`, { email, lang })
      );
      return { success: true };
    } catch (err: any) {
      return { success: false, error: humanizeError(err, 'Resend failed') };
    }
  }

  async resetPassword(email: string, lang?: string): Promise<{ success: boolean; error?: string }> {
    try {
      await firstValueFrom(
        this.http.post(`${this.authUrl}/reset-password`, { email, lang })
      );
      return { success: true };
    } catch (err: any) {
      return { success: false, error: humanizeError(err, 'Reset failed') };
    }
  }

  /** Completes a password reset using the emailed token and a new password. */
  async confirmPasswordReset(token: string, newPassword: string): Promise<{ success: boolean; error?: string }> {
    try {
      await firstValueFrom(
        this.http.post(`${this.authUrl}/reset-password/confirm`, { token, newPassword })
      );
      return { success: true };
    } catch (err: any) {
      return { success: false, error: humanizeError(err, 'Reset failed') };
    }
  }

  async logout(): Promise<void> {
    const refreshToken = this.tokenService.getRefreshToken();
    try {
      await firstValueFrom(
        this.http.post(`${this.authUrl}/logout`, { refreshToken })
      );
    } catch {
      // Best-effort logout
    }
    this.tokenService.clearTokens();
  }
}
