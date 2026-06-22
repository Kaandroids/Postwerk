import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';
import { TokenService } from './token.service';
import { ExportImportService } from './export-import.service';

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  company: string | null;
  phone: string | null;
  lastLoginAt: string | null;
  lastLoginIp: string | null;
  planId: string | null;
  planName: string | null;
}

/**
 * Manages the authenticated user's profile, including loading, updating, password changes,
 * account deletion, and personal data export.
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly api = inject(ApiService);
  private readonly tokenService = inject(TokenService);
  private readonly exportImport = inject(ExportImportService);

  readonly profile = signal<UserProfile | null>(null);

  async loadProfile(): Promise<void> {
    const p = await firstValueFrom(this.api.get<UserProfile>('/users/me'));
    this.profile.set(p);
  }

  async updateProfile(req: { fullName: string; company?: string; phone?: string }): Promise<UserProfile> {
    const p = await firstValueFrom(this.api.put<UserProfile>('/users/me', req));
    this.profile.set(p);
    return p;
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await firstValueFrom(this.api.post('/users/me/change-password', { currentPassword, newPassword }));
  }

  async deleteAccount(): Promise<void> {
    await firstValueFrom(this.api.delete('/users/me'));
    this.tokenService.clearTokens();
  }

  async exportData(): Promise<void> {
    const response = await firstValueFrom(this.api.getBlob('/users/me/export'));
    const blob = response.body;
    if (!blob) return;
    this.exportImport.downloadBlob(blob, 'postwerk-data-export.json');
  }
}
