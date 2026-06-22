import { Injectable, inject, signal, effect } from '@angular/core';
import { EmailAccount } from '../../models/email-account.model';
import { ImapFolder } from '../../models/email.model';
import { EmailAccountService } from './email-account.service';
import { FolderService } from './folder.service';

/**
 * Maintains the active email account context for the current workspace session.
 *
 * All workspace-scoped queries should reference the active account exposed by this service.
 * Automatically loads IMAP folders when the active account changes.
 */
@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly emailAccountService = inject(EmailAccountService);
  private readonly folderService = inject(FolderService);

  readonly accounts = signal<EmailAccount[]>([]);
  readonly activeAccount = signal<EmailAccount | null>(null);
  readonly inboxUnread = signal<number>(0);
  readonly folders = signal<ImapFolder[]>([]);
  constructor() {
    effect(() => {
      const account = this.activeAccount();
      if (account) {
        this.loadFolders();
      } else {
        this.folders.set([]);
      }
    });
  }

  loadAccounts(): void {
    this.emailAccountService.list().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        const defaultAccount = accounts.find(a => a.isDefault) ?? accounts[0] ?? null;
        if (!this.activeAccount() || !accounts.find(a => a.id === this.activeAccount()?.id)) {
          this.activeAccount.set(defaultAccount);
        }
      },
      error: () => { /* silently ignore — accounts remain unchanged */ },
    });
  }

  switchAccount(id: string): void {
    const account = this.accounts().find(a => a.id === id);
    if (account) {
      this.activeAccount.set(account);
    }
  }

  loadFolders(): void {
    const account = this.activeAccount();
    if (!account) return;
    this.folderService.listFolders(account.id).subscribe({
      next: (folders) => this.folders.set(folders),
      error: () => this.folders.set([]),
    });
  }

  addFolder(folder: ImapFolder): void {
    this.folders.set([...this.folders(), folder]);
  }

  removeFolder(folderId: string): void {
    this.folders.set(this.folders().filter(f => f.id !== folderId));
  }
}
