import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { EmailAccount, EmailAccountRequest } from '../../models/email-account.model';

/**
 * Handles CRUD operations for user email accounts, including IMAP/SMTP connection
 * testing and setting a default account.
 */
@Injectable({ providedIn: 'root' })
export class EmailAccountService {
  private readonly api = inject(ApiService);
  private readonly basePath = '/email-accounts';

  list() {
    return this.api.get<EmailAccount[]>(this.basePath);
  }

  get(id: string) {
    return this.api.get<EmailAccount>(`${this.basePath}/${id}`);
  }

  create(request: EmailAccountRequest) {
    return this.api.post<EmailAccount>(this.basePath, request);
  }

  update(id: string, request: EmailAccountRequest) {
    return this.api.put<EmailAccount>(`${this.basePath}/${id}`, request);
  }

  delete(id: string) {
    return this.api.delete<void>(`${this.basePath}/${id}`);
  }

  setDefault(id: string) {
    return this.api.patch<EmailAccount>(`${this.basePath}/${id}/default`, {});
  }

  testConnection(data: { host: string; port: number; username: string; password: string; ssl: boolean; type: 'imap' | 'smtp' }) {
    return this.api.post<{ success: boolean; message: string }>(`${this.basePath}/test-connection`, data);
  }
}
