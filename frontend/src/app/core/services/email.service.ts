import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { ApiService } from './api.service';
import { Email, EmailPage, EmailSyncResult, ComposeEmail, ComposeEmailResponse, DraftAttachment } from '../../models/email.model';

/**
 * Provides account-scoped email operations including paginated listing with filters,
 * read/star toggling, IMAP synchronization, reprocessing, and attachment downloads.
 */
@Injectable({ providedIn: 'root' })
export class EmailService {
  private readonly api = inject(ApiService);

  private basePath(accountId: string) {
    return `/email-accounts/${accountId}/emails`;
  }

  list(accountId: string, options?: {
    page?: number; size?: number; folder?: string; query?: string; isRead?: boolean;
    dateFrom?: string; dateTo?: string;
    categoryId?: string; processed?: boolean; automationId?: string;
  }) {
    let params = new HttpParams();
    if (options?.page != null) params = params.set('page', options.page);
    if (options?.size != null) params = params.set('size', options.size);
    if (options?.folder) params = params.set('folder', options.folder);
    if (options?.query) params = params.set('query', options.query);
    if (options?.isRead != null) params = params.set('isRead', options.isRead);
    if (options?.dateFrom) params = params.set('dateFrom', options.dateFrom);
    if (options?.dateTo) params = params.set('dateTo', options.dateTo);
    if (options?.categoryId) params = params.set('categoryId', options.categoryId);
    if (options?.processed != null) params = params.set('processed', options.processed);
    if (options?.automationId) params = params.set('automationId', options.automationId);
    return this.api.get<EmailPage>(this.basePath(accountId), { params });
  }

  get(accountId: string, emailId: string) {
    return this.api.get<Email>(`${this.basePath(accountId)}/${emailId}`);
  }

  markRead(accountId: string, emailId: string, read: boolean) {
    return this.api.patch<Email>(`${this.basePath(accountId)}/${emailId}/read`, { read });
  }

  toggleStar(accountId: string, emailId: string) {
    return this.api.patch<Email>(`${this.basePath(accountId)}/${emailId}/star`, {});
  }

  reprocess(accountId: string, emailId: string) {
    return this.api.post<Email>(`${this.basePath(accountId)}/${emailId}/reprocess`, {});
  }

  sync(accountId: string) {
    return this.api.post<EmailSyncResult>(`${this.basePath(accountId)}/sync`, {});
  }

  downloadAttachment(accountId: string, emailId: string, index: number) {
    return this.api.getBlob(`${this.basePath(accountId)}/${emailId}/attachments/${index}`);
  }

  /** Moves an email to Trash; deleting one already in Trash removes it permanently. */
  deleteEmail(accountId: string, emailId: string) {
    return this.api.delete<void>(`${this.basePath(accountId)}/${emailId}`);
  }

  /** Restores a trashed email back to its original folder. */
  restoreEmail(accountId: string, emailId: string) {
    return this.api.post<void>(`${this.basePath(accountId)}/${emailId}/restore`, {});
  }

  /** Permanently deletes all trashed emails in the mailbox. */
  emptyTrash(accountId: string) {
    return this.api.delete<{ deleted: number }>(`${this.basePath(accountId)}/trash`);
  }

  send(accountId: string, request: ComposeEmail) {
    return this.api.post<ComposeEmailResponse>(`${this.basePath(accountId)}/send`, request);
  }

  saveDraft(accountId: string, request: ComposeEmail) {
    return this.api.post<ComposeEmailResponse>(`${this.basePath(accountId)}/drafts`, request);
  }

  updateDraft(accountId: string, draftId: string, request: ComposeEmail) {
    return this.api.put<ComposeEmailResponse>(`${this.basePath(accountId)}/drafts/${draftId}`, request);
  }

  deleteDraft(accountId: string, draftId: string) {
    return this.api.delete<void>(`${this.basePath(accountId)}/drafts/${draftId}`);
  }

  uploadAttachment(accountId: string, draftId: string, file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return this.api.post<DraftAttachment>(`${this.basePath(accountId)}/drafts/${draftId}/attachments`, formData);
  }

  deleteAttachment(accountId: string, draftId: string, attachmentId: string) {
    return this.api.delete<void>(`${this.basePath(accountId)}/drafts/${draftId}/attachments/${attachmentId}`);
  }

  listAttachments(accountId: string, draftId: string) {
    return this.api.get<DraftAttachment[]>(`${this.basePath(accountId)}/drafts/${draftId}/attachments`);
  }
}
