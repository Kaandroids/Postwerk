import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { ImapFolder } from '../../models/email.model';

/**
 * Provides operations for listing and creating IMAP folders on a given email account.
 */
@Injectable({ providedIn: 'root' })
export class FolderService {
  private readonly api = inject(ApiService);

  listFolders(accountId: string) {
    return this.api.get<ImapFolder[]>(`/email-accounts/${accountId}/folders`);
  }

  createFolder(accountId: string, name: string) {
    return this.api.post<ImapFolder>(`/email-accounts/${accountId}/folders`, { name });
  }

  deleteFolder(accountId: string, folderId: string) {
    return this.api.delete<void>(`/email-accounts/${accountId}/folders/${folderId}`);
  }
}
