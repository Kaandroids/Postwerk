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

interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  company?: string;
  phone?: string;
  marketingOptIn: boolean;
  termsAccepted: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenService = inject(TokenService);
  private readonly authUrl = `${environment.apiUrl}/auth`;

  isValidEmail(email: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
  }

  async login(email: string, password: string): Promise<{ success: boolean; error?: string }> {
    try {
      const response = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.authUrl}/login`, { email, password })
      );
      this.tokenService.saveTokens(response.accessToken, response.refreshToken, response.role);
      return { success: true };
    } catch (err: any) {
      const message = humanizeError(err, 'Login failed');
      return { success: false, error: message };
    }
  }

  async register(request: RegisterRequest): Promise<{ success: boolean; error?: string }> {
    try {
      const response = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.authUrl}/register`, request)
      );
      this.tokenService.saveTokens(response.accessToken, response.refreshToken, response.role);
      return { success: true };
    } catch (err: any) {
      const message = humanizeError(err, 'Registration failed');
      return { success: false, error: message };
    }
  }

  async resetPassword(email: string): Promise<{ success: boolean; error?: string }> {
    try {
      await firstValueFrom(
        this.http.post(`${this.authUrl}/reset-password`, { email })
      );
      return { success: true };
    } catch (err: any) {
      const message = humanizeError(err, 'Reset failed');
      return { success: false, error: message };
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
