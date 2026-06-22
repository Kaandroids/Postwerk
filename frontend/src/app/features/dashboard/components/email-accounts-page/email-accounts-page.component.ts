import { ChangeDetectionStrategy, Component, inject, signal, computed, HostListener } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { EmailAccountService } from '../../../../core/services/email-account.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { EmailAccount, EmailAccountRequest } from '../../../../models/email-account.model';
import { v } from '../../../../shared/utils/event.util';
import { humanizeError } from '../../../../shared/utils/error.util';
import { PageContentComponent } from '../page-content/page-content.component';

const EK_PALETTE = [
  { key: 'amber', hex: '#d97706' },
  { key: 'violet', hex: '#7c3aed' },
  { key: 'green', hex: '#059669' },
  { key: 'slate', hex: '#475569' },
  { key: 'red', hex: '#dc2626' },
  { key: 'blue', hex: '#2563eb' },
  { key: 'pink', hex: '#db2777' },
  { key: 'teal', hex: '#0891b2' },
];

function colorHex(color: string): string {
  return EK_PALETTE.find(c => c.key === color)?.hex || color || EK_PALETTE[0].hex;
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/** CRUD page for managing email accounts with IMAP/SMTP configuration and connection testing. */
@Component({
  selector: 'app-email-accounts-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ErrorBannerComponent, EmptyStateComponent, PageContentComponent],
  templateUrl: './email-accounts-page.component.html',
  styleUrls: ['./email-accounts-page.component.scss'],
})
export class EmailAccountsPageComponent {
  protected i18n = inject(I18nService);
  protected workspace = inject(WorkspaceService);
  private emailAccountService = inject(EmailAccountService);
  private confirmDialog = inject(ConfirmDialogService);

  readonly palette = EK_PALETTE;

  // View state
  view = signal<'list' | 'form'>('list');
  editing = signal<EmailAccount | null>(null);

  // Filter / search
  filter = signal<'all' | 'active' | 'warning' | 'paused'>('all');
  query = signal('');

  // Form state
  formName = signal('');
  formEmail = signal('');
  formPassword = signal('');
  formColor = signal('violet');
  formSyncSince = signal(today());
  formIsDefault = signal(false);
  formImapEnabled = signal(false);
  formImapHost = signal('');
  formImapPort = signal('993');
  formImapSsl = signal(true);
  formSmtpEnabled = signal(false);
  formSmtpHost = signal('');
  formSmtpPort = signal('465');
  formSmtpSsl = signal(true);
  showPwd = signal(false);

  error = signal('');
  loading = signal(false);
  imapTesting = signal(false);
  imapTestResult = signal<'' | 'ok' | 'fail'>('');
  smtpTesting = signal(false);
  smtpTestResult = signal<'' | 'ok' | 'fail'>('');

  // Computed
  accounts = this.workspace.accounts;

  counts = computed(() => {
    const accs = this.accounts();
    return {
      all: accs.length,
      active: accs.filter(a => a.isActive).length,
      warning: 0,
      paused: accs.filter(a => !a.isActive).length,
    };
  });

  totalToday = computed(() => this.accounts().length * 5); // placeholder
  totalMonth = computed(() => this.accounts().length * 142); // placeholder – last 30 days
  sendCapable = computed(() => this.accounts().filter(a => a.writeEnabled).length);

  visibleAccounts = computed(() => {
    const accs = this.accounts();
    const f = this.filter();
    const q = this.query().toLowerCase().trim();
    return accs.filter(a => {
      if (f === 'active' && !a.isActive) return false;
      if (f === 'paused' && a.isActive) return false;
      if (q && !a.displayName?.toLowerCase().includes(q) && !a.email.toLowerCase().includes(q)) return false;
      return true;
    });
  });

  filters = [
    { key: 'all' as const, label_de: 'Alle', label_en: 'All' },
    { key: 'active' as const, label_de: 'Aktiv', label_en: 'Active' },
    { key: 'paused' as const, label_de: 'Pausiert', label_en: 'Paused' },
  ];

  @HostListener('document:keydown.escape')
  onEsc(): void {
    if (this.view() === 'form') this.closePanel();
  }

  colorHex(color: string): string {
    return colorHex(color);
  }

  initials(name: string): string {
    return name.split(' ').map(s => s[0]).slice(0, 2).join('').toUpperCase();
  }

  statusLabel(acc: EmailAccount): string {
    if (!acc.isActive) return this.i18n.lang() === 'de' ? 'Pausiert' : 'Paused';
    return this.i18n.lang() === 'de' ? 'Aktiv' : 'Active';
  }

  statusKey(acc: EmailAccount): string {
    return acc.isActive ? 'active' : 'paused';
  }

  openAdd(): void {
    this.editing.set(null);
    this.resetForm();
    this.view.set('form');
  }

  openEdit(acc: EmailAccount): void {
    this.editing.set(acc);
    this.formName.set(acc.displayName || '');
    this.formEmail.set(acc.email);
    this.formPassword.set('');
    this.formColor.set(acc.color || 'violet');
    this.formSyncSince.set(acc.syncFromDate || today());
    this.formIsDefault.set(acc.isDefault);
    this.formImapEnabled.set(acc.readEnabled);
    this.formImapHost.set(acc.imapHost || '');
    this.formImapPort.set(acc.imapPort?.toString() || '993');
    this.formImapSsl.set(true);
    this.formSmtpEnabled.set(acc.writeEnabled);
    this.formSmtpHost.set(acc.smtpHost || '');
    this.formSmtpPort.set(acc.smtpPort?.toString() || '465');
    this.formSmtpSsl.set(true);
    this.showPwd.set(false);
    this.error.set('');
    this.view.set('form');
  }

  closePanel(): void {
    this.view.set('list');
    this.editing.set(null);
  }

  async deleteAccount(acc: EmailAccount): Promise<void> {
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('ea_delete_confirm'),
      message: this.i18n.t('ea_delete_confirm'),
      confirmText: this.i18n.t('confirm_delete'),
      cancelText: this.i18n.t('confirm_cancel'),
    });
    if (!ok) return;
    this.emailAccountService.delete(acc.id).subscribe({
      next: () => this.workspace.loadAccounts(),
      error: (err) => this.error.set(humanizeError(err, 'Error')),
    });
  }

  syncAccount(acc: EmailAccount): void {
    this.error.set('');
    this.emailAccountService.testConnection({
      host: acc.imapHost || '', port: acc.imapPort || 993,
      username: acc.email, password: '', ssl: true, type: 'imap',
    }).subscribe({
      error: (err) => this.error.set(humanizeError(err, this.i18n.t('ea_error_required'))),
    });
  }

  testImap(): void {
    if (!this.formImapHost() || !this.formEmail()) return;
    this.imapTesting.set(true);
    this.imapTestResult.set('');
    this.emailAccountService.testConnection({
      host: this.formImapHost(), port: parseInt(this.formImapPort()) || 993,
      username: this.formEmail(), password: this.formPassword(),
      ssl: this.formImapSsl(), type: 'imap',
    }).subscribe({
      next: (r) => { this.imapTesting.set(false); this.imapTestResult.set(r.success ? 'ok' : 'fail'); },
      error: () => { this.imapTesting.set(false); this.imapTestResult.set('fail'); },
    });
  }

  testSmtp(): void {
    if (!this.formSmtpHost() || !this.formEmail()) return;
    this.smtpTesting.set(true);
    this.smtpTestResult.set('');
    this.emailAccountService.testConnection({
      host: this.formSmtpHost(), port: parseInt(this.formSmtpPort()) || 465,
      username: this.formEmail(), password: this.formPassword(),
      ssl: this.formSmtpSsl(), type: 'smtp',
    }).subscribe({
      next: (r) => { this.smtpTesting.set(false); this.smtpTestResult.set(r.success ? 'ok' : 'fail'); },
      error: () => { this.smtpTesting.set(false); this.smtpTestResult.set('fail'); },
    });
  }

  saveForm(): void {
    this.error.set('');
    if (!this.formName().trim()) { this.error.set(this.i18n.t('ea_error_required')); return; }
    if (!this.formEmail().includes('@')) { this.error.set(this.i18n.t('ea_error_email')); return; }
    if (!this.formImapEnabled() && !this.formSmtpEnabled()) { this.error.set(this.i18n.t('ea_error_perm')); return; }

    this.loading.set(true);
    const req: EmailAccountRequest = {
      email: this.formEmail(),
      displayName: this.formName(),
      color: this.formColor(),
      readEnabled: this.formImapEnabled(),
      writeEnabled: this.formSmtpEnabled(),
      isDefault: this.formIsDefault(),
    };

    if (this.formImapEnabled()) {
      req.imapHost = this.formImapHost();
      req.imapPort = parseInt(this.formImapPort()) || 993;
      req.imapUsername = this.formEmail();
      if (this.formPassword()) req.imapPassword = this.formPassword();
      req.imapSsl = this.formImapSsl();
    }
    if (this.formSmtpEnabled()) {
      req.smtpHost = this.formSmtpHost();
      req.smtpPort = parseInt(this.formSmtpPort()) || 465;
      req.smtpUsername = this.formEmail();
      if (this.formPassword()) req.smtpPassword = this.formPassword();
      req.smtpSsl = this.formSmtpSsl();
    }
    if (this.formSyncSince()) req.syncFromDate = this.formSyncSince();

    const ed = this.editing();
    const op$ = ed
      ? this.emailAccountService.update(ed.id, req)
      : this.emailAccountService.create(req);

    op$.subscribe({
      next: () => {
        this.loading.set(false);
        this.workspace.loadAccounts();
        this.closePanel();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(humanizeError(err, this.i18n.t('ea_error_required')));
      },
    });
  }

  readonly v = v;

  private resetForm(): void {
    this.formName.set('');
    this.formEmail.set('');
    this.formPassword.set('');
    this.formColor.set(EK_PALETTE[Math.floor(Math.random() * EK_PALETTE.length)].key);
    this.formSyncSince.set(today());
    this.formIsDefault.set(false);
    this.formImapEnabled.set(false);
    this.formImapHost.set('');
    this.formImapPort.set('993');
    this.formImapSsl.set(true);
    this.formSmtpEnabled.set(false);
    this.formSmtpHost.set('');
    this.formSmtpPort.set('465');
    this.formSmtpSsl.set(true);
    this.showPwd.set(false);
    this.error.set('');
    this.imapTestResult.set('');
    this.smtpTestResult.set('');
  }
}
