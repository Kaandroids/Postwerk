import { ChangeDetectionStrategy, Component, inject, signal, computed, effect, OnInit, OnDestroy, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { I18nService } from '../../../../core/services/i18n.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { EmailService } from '../../../../core/services/email.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { EmailListItem, Email } from '../../../../models/email.model';
import { PageContentComponent } from '../page-content/page-content.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ClickOutsideDirective } from '../../../../shared/directives/click-outside.directive';
import { FormsModule } from '@angular/forms';
import { ViewportService } from '../../../../core/services/viewport.service';
import { ComposePanelComponent } from '../compose-panel/compose-panel.component';
import { EmailDetailComponent } from '../email-detail/email-detail.component';
import { relativeTime } from '../../../../shared/utils/relative-time.util';

/** Email inbox page with folder-based list, message detail view, compose panel, and filter/search bar. */
@Component({
  selector: 'app-emails-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageContentComponent, IconComponent, ClickOutsideDirective, FormsModule, ComposePanelComponent, EmailDetailComponent],
  templateUrl: './emails-page.component.html',
  styleUrl: './emails-page.component.scss',
})
export class EmailsPageComponent implements OnInit, OnDestroy {
  protected i18n = inject(I18nService);
  private workspace = inject(WorkspaceService);
  private emailService = inject(EmailService);
  private confirmDialog = inject(ConfirmDialogService);
  private route = inject(ActivatedRoute);
  protected viewport = inject(ViewportService);
  private querySub?: Subscription;

  // On phones the four filter dropdowns collapse into a single button + bottom sheet.
  protected filterSheetOpen = signal(false);
  protected activeFilterCount = computed(() =>
    (this.dateFilter() !== 'all' ? 1 : 0) +
    (this.categoryFilter().length > 0 ? 1 : 0) +
    (this.readFilter() !== 'all' ? 1 : 0) +
    (this.automationFilter() !== 'all' ? 1 : 0));

  protected currentFolder = signal<string>('INBOX');
  protected emails = signal<EmailListItem[]>([]);
  protected loading = signal(false);
  protected syncing = signal(false);
  protected expandedId = signal<string | null>(null);
  protected expandedEmail = signal<Email | null>(null);
  protected reprocessing = signal(false);
  protected searchQuery = signal('');
  protected readFilter = signal<'all' | 'unread' | 'read'>('all');
  protected dateFilter = signal<'all' | 'today' | 'week' | 'month' | 'custom'>('all');
  protected categoryFilter = signal<string[]>([]);
  protected automationFilter = signal<'all' | 'yes' | 'no'>('all');
  protected customDateFrom = signal('');
  protected customDateTo = signal('');
  protected currentPage = signal(0);
  protected totalPages = signal(0);
  protected totalElements = signal(0);

  // Dropdown open states
  protected dateOpen = signal(false);
  protected categoryOpen = signal(false);
  protected statusOpen = signal(false);
  protected automationOpen = signal(false);

  protected composePanelRef = viewChild<ComposePanelComponent>('composePanel');
  protected activeAccount = this.workspace.activeAccount;

  protected folderTitle = computed(() => {
    const folder = this.currentFolder();
    switch (folder) {
      case 'INBOX': return this.i18n.t('inbox_title');
      case 'SENT': return this.i18n.t('nav_sent');
      case 'SPAM': return this.i18n.t('nav_spam');
      case 'TRASH': return this.i18n.t('nav_trash');
      case 'DRAFTS': return this.i18n.t('nav_drafts');
      default: return folder;
    }
  });

  // Filter options with lazy i18n labels
  protected dateOptions = [
    { value: 'all' as const, label: () => this.i18n.t('inbox_filter_all') },
    { value: 'today' as const, label: () => this.i18n.t('inbox_filter_today') },
    { value: 'week' as const, label: () => this.i18n.t('inbox_filter_week') },
    { value: 'month' as const, label: () => this.i18n.t('inbox_filter_month') },
    { value: 'custom' as const, label: () => this.i18n.t('inbox_filter_custom') },
  ];

  protected statusOptions = [
    { value: 'all' as const, label: () => this.i18n.t('inbox_filter_all') },
    { value: 'unread' as const, label: () => this.i18n.t('inbox_filter_unread') },
    { value: 'read' as const, label: () => this.i18n.t('inbox_filter_read') },
  ];

  protected automationOptions = [
    { value: 'all' as const, label: () => this.i18n.t('inbox_filter_all') },
    { value: 'yes' as const, label: () => this.i18n.t('inbox_filter_automated') },
    { value: 'no' as const, label: () => this.i18n.t('inbox_filter_not_automated') },
  ];

  // Placeholder categories — will be replaced with real data later
  protected categoryOptions = [
    { value: 'leads', label: () => 'Leads', color: '#3b82f6' },
    { value: 'invoices', label: () => this.i18n.t('inbox_cat_invoices'), color: '#10b981' },
    { value: 'newsletter', label: () => 'Newsletter', color: '#f59e0b' },
    { value: 'system', label: () => 'System', color: '#64748b' },
  ];

  protected unreadCount = computed(() => {
    const count = this.emails().filter(e => !e.isRead).length;
    return count;
  });

  private searchDebounce: ReturnType<typeof setTimeout> | null = null;
  private lastSyncedAccountId: string | null = null;

  constructor() {
    effect(() => {
      const account = this.workspace.activeAccount();
      const _folder = this.currentFolder(); // track folder changes too
      if (!account) return;
      this.currentPage.set(0);
      // Only run a (slow) remote IMAP sync when the active account changes.
      // Folder navigation re-uses the already-synced mailbox and just re-queries
      // the local DB, which keeps switching between folders fast.
      if (account.id !== this.lastSyncedAccountId) {
        this.lastSyncedAccountId = account.id;
        this.syncAndLoad();
      } else {
        this.loadEmails();
      }
    });

  }

  ngOnInit(): void {
    this.querySub = this.route.queryParams.subscribe(params => {
      const folder = params['folder'] || 'INBOX';
      if (folder !== this.currentFolder()) {
        this.currentFolder.set(folder);
      }
    });
  }

  ngOnDestroy(): void {
    this.querySub?.unsubscribe();
  }

  // Dropdown label helpers
  protected getDateLabel(): string {
    const opt = this.dateOptions.find(o => o.value === this.dateFilter());
    return opt ? opt.label() : '';
  }

  protected getStatusLabel(): string {
    const opt = this.statusOptions.find(o => o.value === this.readFilter());
    return opt ? opt.label() : '';
  }

  protected getAutomationLabel(): string {
    const opt = this.automationOptions.find(o => o.value === this.automationFilter());
    return opt ? opt.label() : '';
  }

  protected getCategoryLabel(): string {
    const sel = this.categoryFilter();
    if (sel.length === 0) return this.i18n.t('inbox_filter_all');
    if (sel.length === 1) {
      const cat = this.categoryOptions.find(c => c.value === sel[0]);
      return cat ? cat.label() : sel[0];
    }
    return `${sel.length} ${this.i18n.t('inbox_filter_selected')}`;
  }

  protected isCategorySelected(value: string): boolean {
    return this.categoryFilter().includes(value);
  }

  protected toggleCategory(value: string): void {
    const current = this.categoryFilter();
    if (current.includes(value)) {
      this.categoryFilter.set(current.filter(v => v !== value));
    } else {
      this.categoryFilter.set([...current, value]);
    }
    this.onFilterChange();
  }

  protected setDateFilter(value: string): void {
    this.dateFilter.set(value as any);
    this.dateOpen.set(false);
    if (value !== 'custom') {
      this.customDateFrom.set('');
      this.customDateTo.set('');
      this.onFilterChange();
    }
  }

  protected setStatusFilter(value: string): void {
    this.readFilter.set(value as any);
    this.statusOpen.set(false);
    this.onFilterChange();
  }

  protected setAutomationFilter(value: string): void {
    this.automationFilter.set(value as any);
    this.automationOpen.set(false);
    this.onFilterChange();
  }

  protected onSearchInput(value: string): void {
    this.searchQuery.set(value);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.currentPage.set(0);
      this.loadEmails();
    }, 400);
  }

  protected onFilterChange(): void {
    this.currentPage.set(0);
    this.loadEmails();
  }

  protected onSync(): void {
    this.syncAndLoad();
  }

  protected reprocessEmail(event?: Event): void {
    event?.stopPropagation?.();
    const accountId = this.activeAccount()?.id;
    const emailId = this.expandedId();
    if (!accountId || !emailId) return;

    this.reprocessing.set(true);
    this.emailService.reprocess(accountId, emailId).subscribe({
      next: (updated) => {
        this.expandedEmail.set(updated);
        // Update the list item too
        this.emails.update(list =>
          list.map(e => e.id === emailId ? { ...e, processed: updated.processed, automationTraceCount: updated.automationTraceCount, categories: updated.categories } : e)
        );
        this.reprocessing.set(false);
      },
      error: () => this.reprocessing.set(false),
    });
  }

  /** Reprocess the currently-expanded email (invoked by the detail pane's reprocess output). */
  protected reprocessExpanded(): void {
    this.reprocessEmail();
  }

  protected isDraftsFolder(): boolean {
    return this.currentFolder() === 'DRAFTS';
  }

  protected isSentFolder(): boolean {
    return this.currentFolder() === 'SENT';
  }

  protected isInboxFolder(): boolean {
    const f = this.currentFolder();
    return f === 'INBOX' || f === '' || !f;
  }

  protected isTrashFolder(): boolean {
    return this.currentFolder() === 'TRASH';
  }

  /** Show reply button only for received emails (not sent, not drafts, not spam/trash). */
  protected showReply(): boolean {
    const f = this.currentFolder();
    return f !== 'SENT' && f !== 'DRAFTS';
  }

  /** Show reprocess only for inbox and custom folders. */
  protected showReprocess(): boolean {
    const f = this.currentFolder();
    return f === 'INBOX' || f === '' || !f;
  }

  protected toggleExpand(email: EmailListItem): void {
    // Drafts open in compose panel instead of expanding
    if (this.isDraftsFolder()) {
      this.openDraft(email);
      return;
    }

    if (this.expandedId() === email.id) {
      this.expandedId.set(null);
      this.expandedEmail.set(null);
      return;
    }

    this.expandedId.set(email.id);
    this.expandedEmail.set(null);

    const accountId = this.activeAccount()?.id;
    if (!accountId) return;

    this.emailService.get(accountId, email.id).subscribe({
      next: (full) => {
        this.expandedEmail.set(full);
        if (!email.isRead) {
          this.markAsRead(email);
        }
      },
    });
  }

  protected toggleStar(email: EmailListItem, event?: Event): void {
    event?.stopPropagation?.();
    const accountId = this.activeAccount()?.id;
    if (!accountId) return;

    this.emailService.toggleStar(accountId, email.id).subscribe({
      next: () => {
        this.emails.update(list =>
          list.map(e => e.id === email.id ? { ...e, isStarred: !e.isStarred } : e)
        );
        // Keep the expanded detail pane's star icon in sync when it is the same email.
        const expanded = this.expandedEmail();
        if (expanded && expanded.id === email.id) {
          this.expandedEmail.set({ ...expanded, isStarred: !expanded.isStarred });
        }
      },
    });
  }

  /** Toggle star on the currently-expanded email (invoked by the detail pane's star output). */
  protected starExpanded(): void {
    const e = this.emails().find(x => x.id === this.expandedId());
    if (e) this.toggleStar(e);
  }

  protected onPageChange(page: number): void {
    this.currentPage.set(page);
    this.loadEmails();
  }

  protected getInitials(email: EmailListItem): string {
    const name = email.fromPersonal || email.fromAddress || '';
    const parts = name.split(/[\s@.]+/).filter(Boolean);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  }

  protected getRelativeTime(dateStr: string): string {
    return relativeTime(dateStr, this.i18n.lang() === 'de');
  }

  private syncAndLoad(): void {
    const accountId = this.activeAccount()?.id;
    if (!accountId) return;

    this.syncing.set(true);
    this.emailService.sync(accountId).subscribe({
      next: () => {
        this.syncing.set(false);
        this.workspace.loadFolders();
        this.loadEmails();
      },
      error: () => {
        this.syncing.set(false);
        this.loadEmails();
      },
    });
  }

  private loadEmails(): void {
    const accountId = this.activeAccount()?.id;
    if (!accountId) return;

    this.loading.set(true);

    const options: { page: number; size: number; folder?: string; query?: string; isRead?: boolean; dateFrom?: string; dateTo?: string; categoryId?: string; processed?: boolean; automationId?: string } = {
      page: this.currentPage(),
      size: 20,
      folder: this.currentFolder(),
    };

    const query = this.searchQuery().trim();
    if (query) options.query = query;

    const rf = this.readFilter();
    if (rf === 'unread') options.isRead = false;
    else if (rf === 'read') options.isRead = true;

    // Date filter
    const df = this.dateFilter();
    if (df === 'custom') {
      const from = this.customDateFrom();
      const to = this.customDateTo();
      if (from) options.dateFrom = new Date(from).toISOString();
      if (to) {
        // Set to end of day
        const d = new Date(to);
        d.setHours(23, 59, 59, 999);
        options.dateTo = d.toISOString();
      }
    } else if (df !== 'all') {
      const now = new Date();
      let from: Date;
      if (df === 'today') {
        from = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      } else if (df === 'week') {
        from = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      } else {
        // month
        from = new Date(now.getTime() - 31 * 24 * 60 * 60 * 1000);
      }
      options.dateFrom = from.toISOString();
    }

    // Automation filter
    const af = this.automationFilter();
    if (af === 'yes') options.processed = true;
    else if (af === 'no') options.processed = false;

    // Category filter (use first selected for API, multi-select is client-side)
    const cats = this.categoryFilter();
    if (cats.length === 1) options.categoryId = cats[0];

    // When viewing INBOX with unread filter, the main query already gives us the unread count
    const isInboxUnreadQuery = options.folder === 'INBOX' && options.isRead === false
      && !options.query && !options.dateFrom && !options.dateTo
      && !options.categoryId && options.processed === undefined;

    this.emailService.list(accountId, options).subscribe({
      next: (page) => {
        this.emails.set(page.content);
        this.totalPages.set(page.totalPages);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);

        // If we already queried INBOX unread, reuse totalElements
        if (isInboxUnreadQuery) {
          this.workspace.inboxUnread.set(page.totalElements);
        }
      },
      error: () => {
        this.loading.set(false);
      },
    });

    // Only fetch unread count separately when the main query can't provide it
    if (!isInboxUnreadQuery) {
      this.emailService.list(accountId, { page: 0, size: 1, folder: 'INBOX', isRead: false }).subscribe({
        next: (page) => this.workspace.inboxUnread.set(page.totalElements),
        error: () => this.workspace.inboxUnread.set(0),
      });
    }
  }

  protected openCompose(): void {
    this.composePanelRef()?.openCompose();
  }

  protected openReply(): void {
    const email = this.expandedEmail();
    if (email) this.composePanelRef()?.openReply(email);
  }

  protected openForward(): void {
    const email = this.expandedEmail();
    if (email) this.composePanelRef()?.openForward(email);
  }

  protected openDraft(email: EmailListItem): void {
    const accountId = this.activeAccount()?.id;
    if (!accountId) return;

    this.emailService.get(accountId, email.id).subscribe({
      next: (full) => {
        this.composePanelRef()?.openDraft(full);
      },
    });
  }

  protected onDeleteEmail(email: EmailListItem, event?: Event): void {
    event?.stopPropagation?.();
    const accountId = this.activeAccount()?.id;
    if (!accountId) return;

    // Moving an unread inbox email to Trash drops the sidebar unread badge by one immediately
    // (the count otherwise only refreshes on the next inbox load).
    const wasUnreadInbox = !email.isRead && this.isInboxFolder();

    this.emailService.deleteEmail(accountId, email.id).subscribe({
      next: () => {
        this.emails.update(list => list.filter(e => e.id !== email.id));
        if (this.expandedId() === email.id) {
          this.expandedId.set(null);
          this.expandedEmail.set(null);
        }
        this.totalElements.update(n => Math.max(0, n - 1));
        if (wasUnreadInbox) {
          this.workspace.inboxUnread.update(n => Math.max(0, n - 1));
        }
      },
    });
  }

  /** Delete the currently-expanded email (invoked by the detail pane's deleted output). */
  protected deleteExpanded(): void {
    const e = this.emails().find(x => x.id === this.expandedId());
    if (e) this.onDeleteEmail(e);
  }

  /** Restores a trashed email back to its original folder (Papierkorb view). */
  protected onRestoreEmail(email: EmailListItem, event?: Event): void {
    event?.stopPropagation?.();
    const accountId = this.activeAccount()?.id;
    if (!accountId) return;

    this.emailService.restoreEmail(accountId, email.id).subscribe({
      next: () => {
        this.emails.update(list => list.filter(e => e.id !== email.id));
        if (this.expandedId() === email.id) {
          this.expandedId.set(null);
          this.expandedEmail.set(null);
        }
        this.totalElements.update(n => Math.max(0, n - 1));
        // The restored email may re-enter the inbox unread set — re-sync the badge from the server.
        this.refreshInboxUnread();
      },
    });
  }

  /** Empties the Trash (permanently deletes all trashed emails) after confirmation. */
  protected async onEmptyTrash(): Promise<void> {
    const accountId = this.activeAccount()?.id;
    if (!accountId) return;

    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('inbox_empty_trash'),
      message: this.i18n.t('inbox_empty_trash_confirm'),
      confirmText: this.i18n.t('inbox_empty_trash'),
      cancelText: this.i18n.t('confirm_cancel'),
    });
    if (!ok) return;

    this.emailService.emptyTrash(accountId).subscribe({
      next: () => {
        this.emails.set([]);
        this.totalElements.set(0);
        this.expandedId.set(null);
        this.expandedEmail.set(null);
      },
    });
  }

  /** Re-fetches the inbox unread count and updates the sidebar badge signal. */
  private refreshInboxUnread(): void {
    const accountId = this.activeAccount()?.id;
    if (!accountId) return;
    this.emailService.list(accountId, { page: 0, size: 1, folder: 'INBOX', isRead: false }).subscribe({
      next: (page) => this.workspace.inboxUnread.set(page.totalElements),
    });
  }

  protected onComposeSent(): void {
    this.loadEmails();
  }

  private markAsRead(email: EmailListItem): void {
    const accountId = this.activeAccount()?.id;
    if (!accountId) return;

    this.emailService.markRead(accountId, email.id, true).subscribe({
      next: () => {
        this.emails.update(list =>
          list.map(e => e.id === email.id ? { ...e, isRead: true } : e)
        );
      },
    });
  }
}
