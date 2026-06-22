import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, effect, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminOrganizationService } from '../../../../core/services/admin-organization.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { GrantCreditDialogComponent } from '../../../../shared/components/grant-credit-dialog/grant-credit-dialog.component';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import { AdminOrg, AdminOrgDetail } from '../../../../models/admin-org.model';
import { AdminAutomationSummary, AdminMailbox, AdminAuditLog } from '../../../../models/admin.model';

type TypeFilter = '' | 'team' | 'personal';
type OrgTab = 'overview' | 'members' | 'mailboxes' | 'automations' | 'usage' | 'billing' | 'audit' | 'marketplace' | 'gdpr';

/** Platform-staff Organizations screen: searchable/filterable tenant list + centered detail modal. */
@Component({
  selector: 'app-admin-organizations',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, GrantCreditDialogComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-organizations.component.html',
  styleUrl: './admin-organizations.component.scss',
})
export class AdminOrganizationsComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private orgService = inject(AdminOrganizationService);
  private adminService = inject(AdminService);
  private confirmDialog = inject(ConfirmDialogService);
  private timers = managedTimers();

  orgs = signal<AdminOrg[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  currentPage = signal(0);
  loading = signal(true);
  error = signal('');

  search = signal('');
  typeFilter = signal<TypeFilter>('');

  /** Whether the centered detail modal is mounted (true even while detail is loading). */
  detailOpen = signal(false);
  /** Currently opened org detail (null while loading or on error). */
  detail = signal<AdminOrgDetail | null>(null);
  detailLoading = signal(false);
  activeTab = signal<OrgTab>('overview');
  /** True while an action (suspend/activate/transfer/delete) is in flight — disables action buttons. */
  busy = signal(false);
  copied = signal(false);
  /** Member selected for ownership transfer. */
  transferTarget = signal<string>('');

  // ── Grant AI credit shortcut ──
  grantCreditOpen = signal(false);
  /** Transient success flash shown in the modal action area. */
  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Lazy-loaded detail-tab data (fetched on first open of each tab per org) ──
  automationsLoading = signal(false);
  automationsData = signal<AdminAutomationSummary[] | null>(null);
  automationsError = signal(false);

  mailboxesLoading = signal(false);
  mailboxesData = signal<AdminMailbox[] | null>(null);
  mailboxesError = signal(false);

  auditLoading = signal(false);
  auditData = signal<AdminAuditLog[] | null>(null);
  auditError = signal(false);

  readonly tabs: readonly { key: OrgTab; labelKey: string }[] = [
    { key: 'overview', labelKey: 'admin_org_tab_overview' },
    { key: 'members', labelKey: 'admin_org_tab_members' },
    { key: 'mailboxes', labelKey: 'admin_org_tab_mailboxes' },
    { key: 'automations', labelKey: 'admin_org_tab_automations' },
    { key: 'usage', labelKey: 'admin_org_tab_usage' },
    { key: 'billing', labelKey: 'admin_org_tab_billing' },
    { key: 'audit', labelKey: 'admin_org_tab_audit' },
    { key: 'marketplace', labelKey: 'admin_org_tab_marketplace' },
    { key: 'gdpr', labelKey: 'admin_org_tab_gdpr' },
  ];

  /** AI cost for the active month, formatted as a localized EUR string (micros / 1_000_000). */
  aiCostEur = computed(() => {
    const d = this.detail();
    const eur = d ? d.aiCostMicrosThisMonth / 1_000_000 : 0;
    const locale = this.i18n.lang() === 'de' ? 'de-DE' : 'en-US';
    return eur.toLocaleString(locale, { style: 'currency', currency: 'EUR' });
  });

  /** Members eligible to receive ownership (everyone except the current owner). */
  transferCandidates = computed(() => {
    const d = this.detail();
    if (!d) return [];
    return d.members.filter(m => m.userId !== d.ownerUserId);
  });

  constructor() {
    // Lazy-load each wired tab's data when first opened for the loaded org.
    effect(() => {
      const d = this.detail();
      const tab = this.activeTab();
      if (!d) return;
      switch (tab) {
        case 'automations': this.ensureAutomations(d.id); break;
        case 'mailboxes': this.ensureMailboxes(d.id); break;
        case 'audit': this.ensureAudit(d.id); break;
      }
    });
  }

  ngOnInit() {
    this.loadOrgs();
  }

  // ── Lazy tab loaders (idempotent: skip if already loaded/loading) ────
  private ensureAutomations(orgId: string) {
    if (this.automationsData() !== null || this.automationsLoading()) return;
    this.automationsLoading.set(true);
    this.automationsError.set(false);
    this.orgService.getAutomations(orgId).subscribe({
      next: rows => { this.automationsData.set(rows); this.automationsLoading.set(false); },
      error: () => { this.automationsError.set(true); this.automationsLoading.set(false); },
    });
  }

  private ensureMailboxes(orgId: string) {
    if (this.mailboxesData() !== null || this.mailboxesLoading()) return;
    this.mailboxesLoading.set(true);
    this.mailboxesError.set(false);
    this.orgService.getMailboxes(orgId).subscribe({
      next: rows => { this.mailboxesData.set(rows); this.mailboxesLoading.set(false); },
      error: () => { this.mailboxesError.set(true); this.mailboxesLoading.set(false); },
    });
  }

  private ensureAudit(orgId: string) {
    if (this.auditData() !== null || this.auditLoading()) return;
    this.auditLoading.set(true);
    this.auditError.set(false);
    this.adminService.getAuditLog(undefined, undefined, 0, 50, orgId).subscribe({
      next: page => { this.auditData.set(page.content); this.auditLoading.set(false); },
      error: () => { this.auditError.set(true); this.auditLoading.set(false); },
    });
  }

  /** Clears all lazy-loaded tab data so a freshly opened org re-fetches. */
  private resetTabData() {
    this.automationsData.set(null); this.automationsLoading.set(false); this.automationsError.set(false);
    this.mailboxesData.set(null); this.mailboxesLoading.set(false); this.mailboxesError.set(false);
    this.auditData.set(null); this.auditLoading.set(false); this.auditError.set(false);
  }

  loadOrgs() {
    this.loading.set(true);
    this.error.set('');
    const personal = this.typeFilter() === '' ? undefined : this.typeFilter() === 'personal';
    this.orgService.list(this.search(), personal, this.currentPage()).subscribe({
      next: page => {
        this.orgs.set(page.content);
        this.totalElements.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.orgs.set([]);
        this.loading.set(false);
        this.error.set(this.i18n.t('admin_org_load_failed'));
      },
    });
  }

  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  onSearch(event: Event) {
    this.search.set((event.target as HTMLInputElement).value);
    if (this.searchTimeout) this.timers.clear(this.searchTimeout);
    this.searchTimeout = this.timers.set(() => {
      this.currentPage.set(0);
      this.loadOrgs();
    }, 400);
  }

  onTypeFilter(event: Event) {
    this.typeFilter.set((event.target as HTMLSelectElement).value as TypeFilter);
    this.currentPage.set(0);
    this.loadOrgs();
  }

  goToPage(page: number) {
    this.currentPage.set(page);
    this.loadOrgs();
  }

  /** First letter (uppercased) of an org name, for the square avatar. */
  initial(name: string): string {
    return (name?.trim()?.[0] ?? '?').toUpperCase();
  }

  /** Deterministic hue from the org name so the gradient avatar is stable per org. */
  avatarHue(name: string): number {
    const s = name ?? '';
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
    return h;
  }

  /** Badge tone for an automation status. */
  automationStatusTone(status: string): string {
    switch ((status ?? '').toUpperCase()) {
      case 'ACTIVE': return 'green';
      case 'TESTING': return 'amber';
      case 'PAUSED': return 'slate';
      case 'ERROR': return 'danger';
      default: return 'slate';
    }
  }

  /** Badge tone for an automation kind. */
  kindTone(kind: string): string {
    return (kind ?? '').toUpperCase() === 'INTEGRATION' ? 'plum' : 'slate';
  }

  openDetail(org: AdminOrg) {
    this.activeTab.set('overview');
    this.transferTarget.set('');
    this.error.set('');
    this.resetTabData();
    this.detailLoading.set(true);
    this.detail.set(null);
    this.orgService.get(org.id).subscribe({
      next: d => {
        this.detail.set(d);
        this.detailLoading.set(false);
      },
      error: () => {
        this.detailLoading.set(false);
        this.error.set(this.i18n.t('admin_org_load_failed'));
      },
    });
    // Mark modal open immediately so the scrim renders during the fetch.
    this.detailOpen.set(true);
  }

  closeDetail() {
    this.detailOpen.set(false);
    this.detail.set(null);
    this.transferTarget.set('');
    this.grantCreditOpen.set(false);
    this.flash.set('');
  }

  // ── Grant AI credit shortcut ────────────────────────────────────────
  openGrantCredit() {
    if (!this.identity.has('QUOTA_OVERRIDE')) return;
    this.grantCreditOpen.set(true);
  }

  onCreditGranted(e: { amountCents: number; targetName: string }) {
    this.grantCreditOpen.set(false);
    const locale = this.i18n.lang() === 'de' ? 'de-DE' : 'en-US';
    const amount = (e.amountCents / 100).toLocaleString(locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
    this.flash.set(this.i18n.t('gcd_flash', { amount, name: e.targetName }));
    if (this.flashTimer) this.timers.clear(this.flashTimer);
    this.flashTimer = this.timers.set(() => this.flash.set(''), 4000);
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.grantCreditOpen()) { this.grantCreditOpen.set(false); return; }
    if (this.detailOpen()) this.closeDetail();
  }

  /** Permission required to open a detail tab, or null if the tab is ungated. */
  tabPermission(tab: OrgTab): string | null {
    // Automations + mailboxes + usage are ORG_VIEW-only (same as opening the org).
    if (tab === 'billing') return 'BILLING_VIEW';
    if (tab === 'audit') return 'AUDIT_LOG_VIEW';
    return null;
  }

  /** Whether the given detail tab is locked for the current staff role. */
  tabLocked(tab: OrgTab): boolean {
    const perm = this.tabPermission(tab);
    return perm !== null && !this.identity.has(perm);
  }

  setTab(tab: OrgTab) {
    if (this.tabLocked(tab)) return;
    this.activeTab.set(tab);
  }

  copyId() {
    const d = this.detail();
    if (!d) return;
    try {
      navigator.clipboard?.writeText(d.id);
      this.copied.set(true);
      this.timers.set(() => this.copied.set(false), 1200);
    } catch {
      /* clipboard unavailable — ignore */
    }
  }

  private applyDetail(updated: AdminOrgDetail) {
    this.detail.set(updated);
    // Keep the list row in sync with the new suspended state.
    this.orgs.update(list => list.map(o => o.id === updated.id ? { ...o, suspendedAt: updated.suspendedAt } : o));
  }

  async toggleSuspend() {
    const d = this.detail();
    if (!d || this.busy()) return;
    this.error.set('');

    if (d.suspendedAt) {
      this.busy.set(true);
      this.orgService.activate(d.id).subscribe({
        next: updated => { this.applyDetail(updated); this.busy.set(false); },
        error: () => { this.busy.set(false); this.error.set(this.i18n.t('admin_org_action_failed')); },
      });
      return;
    }

    const confirmed = await this.confirmDialog.confirm({
      title: this.i18n.t('admin_org_suspend_title'),
      message: this.i18n.t('admin_org_suspend_msg', { name: d.name }),
      confirmText: this.i18n.t('admin_org_suspend'),
      cancelText: this.i18n.t('confirm_cancel'),
    });
    if (!confirmed) return;

    // Optional free-text reason captured via a lightweight prompt.
    const reason = typeof window !== 'undefined'
      ? (window.prompt(this.i18n.t('admin_org_suspend_reason_prompt')) ?? undefined)
      : undefined;

    this.busy.set(true);
    this.orgService.suspend(d.id, reason && reason.trim() ? reason.trim() : undefined).subscribe({
      next: updated => { this.applyDetail(updated); this.busy.set(false); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('admin_org_action_failed')); },
    });
  }

  transferOwnership() {
    const d = this.detail();
    const target = this.transferTarget();
    if (!d || d.personal || !target || this.busy()) return;
    this.error.set('');
    this.busy.set(true);
    this.orgService.transferOwnership(d.id, target).subscribe({
      next: updated => { this.applyDetail(updated); this.transferTarget.set(''); this.busy.set(false); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('admin_org_action_failed')); },
    });
  }

  async deleteOrg() {
    const d = this.detail();
    if (!d || d.personal || this.busy()) return;
    this.error.set('');
    const confirmed = await this.confirmDialog.confirm({
      title: this.i18n.t('admin_org_delete_title'),
      message: this.i18n.t('admin_org_delete_msg', { name: d.name }),
      confirmText: this.i18n.t('confirm_delete'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!confirmed) return;
    this.busy.set(true);
    this.orgService.remove(d.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.closeDetail();
        this.loadOrgs();
      },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('admin_org_action_failed')); },
    });
  }
}
