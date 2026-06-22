import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminSubscriptionService } from '../../../../core/services/admin-subscription.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminOrganizationService } from '../../../../core/services/admin-organization.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { GrantCreditDialogComponent } from '../../../../shared/components/grant-credit-dialog/grant-credit-dialog.component';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import { PlanModel, PlanRequest } from '../../../../models/admin.model';
import {
  Subscription,
  SubscriptionDetail,
  SubscriptionKpis,
  SubscriptionFilters,
  SubscriptionStatus,
} from '../../../../models/admin-subscription.model';
import { planTone } from '../../../../shared/utils/admin-format.util';

type ModalTab = 'overview' | 'history';
type SortKey = 'orgName' | 'planName' | 'usage';
type CapMode = 'off' | 'capped' | 'unlimited';

/**
 * Platform-staff Plans & Subscriptions: KPI strip + plan catalog (cards + editor) + filterable/
 * paginated subscriptions table + detail modal (change plan / grant credit / suspend). Payment is
 * metadata-only — MRR is derived. Mutations are UX-gated (PLAN_MANAGE / ORG_MANAGE / QUOTA_OVERRIDE);
 * the backend enforces every one.
 */
@Component({
  selector: 'app-admin-subscriptions',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, GrantCreditDialogComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-subscriptions.component.html',
  styleUrl: './admin-subscriptions.component.scss',
})
export class AdminSubscriptionsComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private service = inject(AdminSubscriptionService);
  private adminService = inject(AdminService);
  private orgService = inject(AdminOrganizationService);
  private confirmDialog = inject(ConfirmDialogService);
  private timers = managedTimers();

  // ── List state ────────────────────────────────────────────────────────────
  rows = signal<Subscription[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  currentPage = signal(0);
  readonly pageSize = 10;
  loading = signal(true);
  error = signal('');

  kpis = signal<SubscriptionKpis | null>(null);
  plans = signal<PlanModel[]>([]);

  refreshing = signal(false);
  updatedAt = signal('');
  alertDismissed = signal(false);

  // ── Filters ─────────────────────────────────────────────────────────────────
  search = signal('');
  planFilter = signal('');
  statusFilter = signal<'' | SubscriptionStatus>('');
  usageFilter = signal<'' | 'over90' | 'unlimited' | 'aiOff'>('');
  sortKey = signal<SortKey>('orgName');
  sortDir = signal<'asc' | 'desc'>('asc');

  hasActiveFilters = computed(() =>
    !!this.search() || !!this.planFilter() || !!this.statusFilter() || !!this.usageFilter());

  readonly sortedRows = computed(() => {
    const list = [...this.rows()];
    const dir = this.sortDir() === 'asc' ? 1 : -1;
    switch (this.sortKey()) {
      case 'orgName': list.sort((a, b) => a.orgName.localeCompare(b.orgName) * dir); break;
      case 'planName': list.sort((a, b) => (a.planName ?? '').localeCompare(b.planName ?? '') * dir); break;
      case 'usage': list.sort((a, b) => (this.usageRatio(a) - this.usageRatio(b)) * dir); break;
    }
    return list;
  });

  // ── Flash ───────────────────────────────────────────────────────────────────
  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Detail modal ────────────────────────────────────────────────────────────
  detailOpen = signal(false);
  detail = signal<SubscriptionDetail | null>(null);
  detailLoading = signal(false);
  modalTab = signal<ModalTab>('overview');
  busy = signal(false);
  changePlanId = signal('');
  grantCreditOpen = signal(false);

  // ── Plan editor modal ────────────────────────────────────────────────────────
  editorOpen = signal(false);
  editorMode = signal<'new' | 'edit'>('new');
  editingPlanId = signal<string | null>(null);
  editorSaving = signal(false);
  editorError = signal('');
  // form
  eName = signal('');
  ePrice = signal('');
  eCapMode = signal<CapMode>('capped');
  eCapAmount = signal('');
  eTokenLimit = signal('');
  eAutomationLimit = signal('');
  eMailboxLimit = signal('');
  eMarketplace = signal(true);
  // Not surfaced as form fields here (edited on the dedicated Plans page); preserved on save so this
  // editor never silently resets them. Seeded from the plan on edit, defaulted on create.
  eApiWebhook = signal(false);
  eInboundWebhookLimit = signal(0);
  eAttempted = signal(false);

  ngOnInit() {
    this.refreshAll();
  }

  // ── Data loading ──────────────────────────────────────────────────────────
  private filters(): SubscriptionFilters {
    return {
      search: this.search() || undefined,
      plan: this.planFilter() || undefined,
      status: this.statusFilter() || undefined,
      usage: this.usageFilter() || undefined,
    };
  }

  loadRows() {
    this.loading.set(true);
    this.error.set('');
    this.service.list(this.filters(), this.currentPage(), this.pageSize).subscribe({
      next: page => {
        this.rows.set(page.content);
        this.totalElements.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
      },
      error: () => { this.rows.set([]); this.loading.set(false); this.error.set(this.i18n.t('psub_load_failed')); },
    });
  }

  loadKpis() {
    this.service.kpis().subscribe({ next: k => this.kpis.set(k), error: () => {} });
  }

  loadPlans() {
    this.adminService.getPlans().subscribe({ next: p => this.plans.set(p), error: () => {} });
  }

  private stampUpdated() {
    const d = new Date();
    const p = (n: number) => String(n).padStart(2, '0');
    this.updatedAt.set(`${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`);
  }

  private refreshAll() {
    this.loadKpis();
    this.loadPlans();
    this.loadRows();
    this.stampUpdated();
  }

  refresh() {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    this.refreshAll();
    this.timers.set(() => this.refreshing.set(false), 600);
  }

  // ── Filters / search ─────────────────────────────────────────────────────────
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  onSearch(event: Event) {
    this.search.set((event.target as HTMLInputElement).value);
    if (this.searchTimer) this.timers.clear(this.searchTimer);
    this.searchTimer = this.timers.set(() => { this.currentPage.set(0); this.loadRows(); }, 400);
  }

  onPlanFilter(event: Event) { this.planFilter.set((event.target as HTMLSelectElement).value); this.currentPage.set(0); this.loadRows(); }
  onStatusFilter(event: Event) { this.statusFilter.set((event.target as HTMLSelectElement).value as '' | SubscriptionStatus); this.currentPage.set(0); this.loadRows(); }
  onUsageFilter(event: Event) { this.usageFilter.set((event.target as HTMLSelectElement).value as '' | 'over90' | 'unlimited' | 'aiOff'); this.currentPage.set(0); this.loadRows(); }

  clearFilters() {
    this.search.set(''); this.planFilter.set(''); this.statusFilter.set(''); this.usageFilter.set('');
    this.currentPage.set(0); this.loadRows();
  }

  filterByPlan(name: string) { this.planFilter.set(name); this.currentPage.set(0); this.loadRows(); }
  inspectOverCap() { this.usageFilter.set('over90'); this.planFilter.set(''); this.statusFilter.set(''); this.search.set(''); this.alertDismissed.set(true); this.currentPage.set(0); this.loadRows(); }

  toggleSort(key: SortKey) {
    if (this.sortKey() === key) this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    else { this.sortKey.set(key); this.sortDir.set(key === 'orgName' ? 'asc' : 'desc'); }
  }

  goToPage(page: number) { if (page < 0 || page >= this.totalPages()) return; this.currentPage.set(page); this.loadRows(); }

  // ── Display helpers ──────────────────────────────────────────────────────────
  initial(name: string): string { return (name?.trim()?.[0] ?? '?').toUpperCase(); }
  avatarHue(seed: string): number {
    const s = seed ?? ''; let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
    return h;
  }

  /** Canonical plan→badge-tone mapping, shared with the other admin pages. */
  protected planTone = planTone;

  centsFromMicros(micros: number): number { return micros / 10_000; }
  eurFromMicros(micros: number): string { return '€' + (micros / 1_000_000).toFixed(2); }
  eurFromCents(cents: number): string { return '€' + (cents / 100).toFixed(2); }
  eur0(value: number): string {
    const locale = this.i18n.lang() === 'de' ? 'de-DE' : 'en-US';
    return value.toLocaleString(locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
  }

  /** AI cap label for a plan/effective cents value. */
  capLabel(cents: number): string {
    if (cents < 0) return '∞ ' + this.i18n.t('psub_unlimited');
    if (cents === 0) return this.i18n.t('psub_ai_off');
    return this.eurFromCents(cents);
  }

  limitLabel(n: number): string { return n < 0 ? '∞' : String(n); }

  /** AI usage ratio over the effective cap (0..1; 0 when unlimited/off). */
  usageRatio(s: { aiCostMicrosThisMonth: number; effectiveCapCents: number }): number {
    if (s.effectiveCapCents <= 0) return 0;
    const ratio = this.centsFromMicros(s.aiCostMicrosThisMonth) / s.effectiveCapCents;
    return Math.min(Math.max(ratio, 0), 1);
  }
  usageTone(s: { aiCostMicrosThisMonth: number; effectiveCapCents: number }): string {
    if (s.effectiveCapCents < 0) return 'unlim';
    const r = this.usageRatio(s);
    if (r > 0.9) return 'danger';
    if (r > 0.7) return 'amber';
    return 'green';
  }

  // ── Detail modal ────────────────────────────────────────────────────────────
  openDetail(s: Subscription) {
    this.modalTab.set('overview');
    this.error.set('');
    this.changePlanId.set('');
    this.detailLoading.set(true);
    this.detail.set(null);
    this.detailOpen.set(true);
    this.service.get(s.orgId).subscribe({
      next: d => { this.detail.set(d); this.changePlanId.set(d.planId ?? ''); this.detailLoading.set(false); },
      error: () => { this.detailLoading.set(false); this.error.set(this.i18n.t('psub_load_failed')); },
    });
  }

  closeDetail() { this.detailOpen.set(false); this.detail.set(null); this.grantCreditOpen.set(false); }
  setTab(tab: ModalTab) { this.modalTab.set(tab); }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.grantCreditOpen()) { this.grantCreditOpen.set(false); return; }
    if (this.editorOpen()) { this.editorOpen.set(false); return; }
    if (this.detailOpen()) this.closeDetail();
  }

  onChangePlanSelect(event: Event) { this.changePlanId.set((event.target as HTMLSelectElement).value); }

  async changePlan() {
    const d = this.detail();
    const planId = this.changePlanId();
    if (!d || !planId || planId === d.planId || !this.identity.has('PLAN_MANAGE') || this.busy()) return;
    const newPlan = this.plans().find(p => p.id === planId);
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('psub_change_plan_title'),
      message: this.i18n.t('psub_change_plan_msg', { name: d.orgName, plan: newPlan?.name ?? '' }),
      confirmText: this.i18n.t('psub_change_plan'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'accent',
    });
    if (!ok) return;
    this.busy.set(true);
    this.service.changePlan(d.orgId, planId, null).subscribe({
      next: updated => {
        this.busy.set(false);
        this.detail.set(updated);
        this.changePlanId.set(updated.planId ?? '');
        this.rows.update(list => list.map(r => r.orgId === updated.orgId ? { ...r, planName: updated.planName, effectiveCapCents: updated.effectiveCapCents } : r));
        this.loadKpis();
        this.loadPlans();
        this.flashMsg(this.i18n.t('psub_flash_plan_changed', { name: d.orgName, plan: updated.planName ?? '' }));
      },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('psub_action_failed')); },
    });
  }

  async toggleSuspend() {
    const d = this.detail();
    if (!d || d.personal || !this.identity.has('ORG_MANAGE') || this.busy()) return;
    if (d.status === 'suspended') {
      this.busy.set(true);
      this.orgService.activate(d.orgId).subscribe({
        next: () => { this.busy.set(false); this.applyStatus(d.orgId, 'active'); this.flashMsg(this.i18n.t('psub_flash_activated', { name: d.orgName })); },
        error: () => { this.busy.set(false); this.error.set(this.i18n.t('psub_action_failed')); },
      });
      return;
    }
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('admin_org_suspend_title'),
      message: this.i18n.t('admin_org_suspend_msg', { name: d.orgName }),
      confirmText: this.i18n.t('admin_org_suspend'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!ok) return;
    this.busy.set(true);
    this.orgService.suspend(d.orgId, undefined).subscribe({
      next: () => { this.busy.set(false); this.applyStatus(d.orgId, 'suspended'); this.flashMsg(this.i18n.t('psub_flash_suspended', { name: d.orgName })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('psub_action_failed')); },
    });
  }

  private applyStatus(orgId: string, status: SubscriptionStatus) {
    if (this.detail()?.orgId === orgId) this.detail.update(d => d ? { ...d, status, suspendedAt: status === 'suspended' ? new Date().toISOString() : null } : d);
    this.rows.update(list => list.map(r => r.orgId === orgId ? { ...r, status } : r));
    this.loadKpis();
  }

  // ── Grant credit (reuse the shared dialog) ────────────────────────────────────
  openGrantCredit() { if (this.identity.has('QUOTA_OVERRIDE')) this.grantCreditOpen.set(true); }
  onCreditGranted(e: { amountCents: number; targetName: string }) {
    this.grantCreditOpen.set(false);
    this.flashMsg(this.i18n.t('psub_flash_credit', { amount: this.eurFromCents(e.amountCents), name: e.targetName }));
    const d = this.detail();
    if (d) this.service.get(d.orgId).subscribe({ next: fresh => this.detail.set(fresh), error: () => {} });
  }

  // ── Plan editor ────────────────────────────────────────────────────────────
  openNewPlan() {
    if (!this.identity.has('PLAN_MANAGE')) return;
    this.editorMode.set('new');
    this.editingPlanId.set(null);
    this.eName.set(''); this.ePrice.set(''); this.eCapMode.set('capped'); this.eCapAmount.set('');
    this.eTokenLimit.set('10000'); this.eAutomationLimit.set('5'); this.eMailboxLimit.set('2');
    this.eMarketplace.set(true); this.eApiWebhook.set(false); this.eInboundWebhookLimit.set(0);
    this.eAttempted.set(false); this.editorError.set('');
    this.editorOpen.set(true);
  }

  openEditPlan(p: PlanModel, event?: Event) {
    event?.stopPropagation();
    if (!this.identity.has('PLAN_MANAGE')) return;
    this.editorMode.set('edit');
    this.editingPlanId.set(p.id);
    this.eName.set(p.name);
    this.ePrice.set(String(p.price));
    this.eCapMode.set(p.costLimitCents < 0 ? 'unlimited' : p.costLimitCents === 0 ? 'off' : 'capped');
    this.eCapAmount.set(p.costLimitCents > 0 ? String(Math.round(p.costLimitCents / 100)) : '');
    this.eTokenLimit.set(String(p.tokenLimit));
    this.eAutomationLimit.set(String(p.automationLimit));
    this.eMailboxLimit.set(String(p.emailAccountLimit));
    this.eMarketplace.set(p.marketplacePublishEnabled);
    this.eApiWebhook.set(p.apiWebhookEnabled);
    this.eInboundWebhookLimit.set(p.inboundWebhookLimit);
    this.eAttempted.set(false); this.editorError.set('');
    this.editorOpen.set(true);
  }

  closeEditor() { this.editorOpen.set(false); }
  setCapMode(m: CapMode) { this.eCapMode.set(m); }

  readonly editorNameMissing = computed(() => !this.eName().trim());
  readonly editorCapMissing = computed(() => this.eCapMode() === 'capped' && !(parseFloat(this.eCapAmount()) > 0));

  private costLimitCents(): number {
    if (this.eCapMode() === 'unlimited') return -1;
    if (this.eCapMode() === 'off') return 0;
    return Math.round((parseFloat(this.eCapAmount()) || 0) * 100);
  }

  private intOr(value: string, fallback: number): number {
    const n = parseInt(value, 10);
    return isNaN(n) ? fallback : n;
  }

  savePlan() {
    if (!this.identity.has('PLAN_MANAGE') || this.editorSaving()) return;
    this.eAttempted.set(true);
    if (this.editorNameMissing() || this.editorCapMissing()) return;
    const req: PlanRequest = {
      name: this.eName().trim(),
      tokenLimit: this.intOr(this.eTokenLimit(), 0),
      automationLimit: this.intOr(this.eAutomationLimit(), 0),
      emailAccountLimit: this.intOr(this.eMailboxLimit(), 0),
      price: parseFloat(this.ePrice()) || 0,
      costLimitCents: this.costLimitCents(),
      apiWebhookEnabled: this.eApiWebhook(),
      inboundWebhookLimit: this.eInboundWebhookLimit(),
      marketplacePublishEnabled: this.eMarketplace(),
    };
    this.editorSaving.set(true);
    this.editorError.set('');
    const id = this.editingPlanId();
    const obs = id ? this.adminService.updatePlan(id, req) : this.adminService.createPlan(req);
    obs.subscribe({
      next: () => {
        this.editorSaving.set(false);
        this.editorOpen.set(false);
        this.loadPlans();
        this.loadKpis();
        this.loadRows();
        this.flashMsg(this.i18n.t(id ? 'psub_flash_plan_saved' : 'psub_flash_plan_created', { name: req.name }));
      },
      error: () => { this.editorSaving.set(false); this.editorError.set(this.i18n.t('psub_action_failed')); },
    });
  }

  async deletePlan(p: PlanModel, event?: Event) {
    event?.stopPropagation();
    if (!this.identity.has('PLAN_MANAGE')) return;
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('psub_delete_plan_title'),
      message: this.i18n.t('psub_delete_plan_msg', { name: p.name }),
      confirmText: this.i18n.t('admin_org_delete'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!ok) return;
    this.adminService.deletePlan(p.id).subscribe({
      next: () => { this.loadPlans(); this.loadKpis(); this.loadRows(); this.flashMsg(this.i18n.t('psub_flash_plan_deleted', { name: p.name })); },
      error: () => this.error.set(this.i18n.t('psub_action_failed')),
    });
  }

  // ── Misc ───────────────────────────────────────────────────────────────────
  onName(e: Event) { this.eName.set((e.target as HTMLInputElement).value); }
  onPrice(e: Event) { this.ePrice.set((e.target as HTMLInputElement).value); }
  onCapAmount(e: Event) { this.eCapAmount.set((e.target as HTMLInputElement).value); }
  onTokenLimit(e: Event) { this.eTokenLimit.set((e.target as HTMLInputElement).value); }
  onAutomationLimit(e: Event) { this.eAutomationLimit.set((e.target as HTMLInputElement).value); }
  onMailboxLimit(e: Event) { this.eMailboxLimit.set((e.target as HTMLInputElement).value); }
  toggleMarketplace(e: Event) { this.eMarketplace.set((e.target as HTMLInputElement).checked); }

  private flashMsg(msg: string) {
    this.flash.set(msg);
    if (this.flashTimer) this.timers.clear(this.flashTimer);
    this.flashTimer = this.timers.set(() => this.flash.set(''), 3200);
  }
}
