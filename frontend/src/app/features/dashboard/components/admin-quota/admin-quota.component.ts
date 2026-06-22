import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminQuotaService } from '../../../../core/services/admin-quota.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminOrganizationService } from '../../../../core/services/admin-organization.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import {
  QuotaOverride,
  QuotaKpis,
  QuotaOverrideKind,
  QuotaOverrideRequest,
  QuotaOverrideFilters,
  QuotaTargetType,
} from '../../../../models/admin-quota.model';

/** Plan → base monthly cap in cents (mirrors the backend defaults; used for the modal preview only). */
const PLAN_BASE_CAP: Record<string, number> = {
  FREE: 0, Free: 0,
  STARTER: 5000, Starter: 5000,
  PRO: 20000, Pro: 20000,
  BUSINESS: 50000, Business: 50000,
  ENTERPRISE: 200000, Enterprise: 200000,
};

type ModalMode = 'create' | 'edit' | 'view';

/** A merged user/org search result for the target picker. */
interface TargetCandidate {
  type: QuotaTargetType;
  id: string;
  name: string;
  /** email (user) or slug (org). */
  slug: string;
  plan: string | null;
}

/** A picked target carried in the modal form. */
interface PickedTarget extends TargetCandidate {
  /** Known base cap (from a seeded override) so the preview is exact in edit/view. */
  baseCapCents?: number;
}

/**
 * Platform-staff Quota Overrides screen: KPI strip + filterable/paginated table of per-user / per-org
 * AI quota exceptions, with a centered create/edit/view modal. Mutations are gated by QUOTA_OVERRIDE
 * (UX affordance only — the backend enforces every change).
 */
@Component({
  selector: 'app-admin-quota',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-quota.component.html',
  styleUrl: './admin-quota.component.scss',
})
export class AdminQuotaComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private quotaService = inject(AdminQuotaService);
  private adminService = inject(AdminService);
  private orgService = inject(AdminOrganizationService);
  private confirmDialog = inject(ConfirmDialogService);
  private timers = managedTimers();

  // ── List state ────────────────────────────────────────────────────────
  rows = signal<QuotaOverride[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  currentPage = signal(0);
  readonly pageSize = 10;
  loading = signal(true);
  error = signal('');

  kpis = signal<QuotaKpis | null>(null);

  // ── Filters ───────────────────────────────────────────────────────────
  search = signal('');
  targetTypeFilter = signal<'' | QuotaTargetType>('');
  kindFilter = signal<'' | QuotaOverrideKind>('');
  statusFilter = signal<'' | 'active' | 'expired'>('');
  expiryFilter = signal<'' | 'next7' | 'next30'>('');

  hasActiveFilters = computed(() =>
    !!this.search() || !!this.targetTypeFilter() || !!this.kindFilter() ||
    !!this.statusFilter() || !!this.expiryFilter());

  // ── Transient success flash ─────────────────────────────────────────────
  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Modal state ─────────────────────────────────────────────────────────
  modalOpen = signal(false);
  modalMode = signal<ModalMode>('create');
  /** Seed record when editing/viewing (null for create). */
  editing = signal<QuotaOverride | null>(null);
  saving = signal(false);
  modalError = signal('');

  // Form fields
  target = signal<PickedTarget | null>(null);
  formKind = signal<QuotaOverrideKind>('CREDIT');
  /** Amount in whole euros, as typed. */
  amount = signal('');
  expiryDate = signal('');
  noExpiry = signal(false);
  reason = signal('');
  attempted = signal(false);

  // ── Target picker state ──────────────────────────────────────────────────
  pickerQuery = signal('');
  pickerOpen = signal(false);
  pickerLoading = signal(false);
  pickerResults = signal<TargetCandidate[]>([]);
  private pickerTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Derived (modal) ──────────────────────────────────────────────────────
  readonly isView = computed(() => this.modalMode() === 'view');
  readonly targetLocked = computed(() => this.modalMode() !== 'create');

  /** Base monthly cap of the picked target, in cents. */
  readonly baseCapCents = computed(() => {
    const t = this.target();
    if (!t) return 0;
    if (t.baseCapCents != null) return t.baseCapCents;
    return PLAN_BASE_CAP[t.plan ?? ''] ?? 0;
  });

  /** Amount in cents from the typed euro value. */
  readonly amountCents = computed(() => Math.round((parseFloat(this.amount()) || 0) * 100));

  /** Effective cap preview in cents; null = unlimited. */
  readonly previewEffectiveCents = computed<number | null>(() => {
    const kind = this.formKind();
    if (kind === 'UNLIMITED') return null;
    if (kind === 'CREDIT') return this.baseCapCents() + this.amountCents();
    return this.amountCents(); // CAP
  });

  readonly needsAmount = computed(() => this.formKind() !== 'UNLIMITED');

  // Validation
  readonly targetMissing = computed(() => !this.target());
  readonly amountMissing = computed(() => this.needsAmount() && !(this.amountCents() > 0));
  readonly reasonMissing = computed(() => !this.reason().trim());

  readonly kindSegments: readonly { kind: QuotaOverrideKind; labelKey: string; icon: string }[] = [
    { kind: 'CREDIT', labelKey: 'qov_kind_credit', icon: 'creditCard' },
    { kind: 'CAP', labelKey: 'qov_kind_cap', icon: 'sliders' },
    { kind: 'UNLIMITED', labelKey: 'qov_kind_unlimited', icon: 'bolt' },
  ];

  ngOnInit() {
    this.loadKpis();
    this.loadRows();
  }

  // ── Money formatting (whole-euro, locale thousands) ──────────────────────
  /** cents → "€1.234" (de-DE) / "€1,234" (en-US), whole euros. null → unlimited glyph. */
  euro(cents: number | null | undefined): string {
    if (cents == null) return '∞';
    const locale = this.i18n.lang() === 'de' ? 'de-DE' : 'en-US';
    return (cents / 100).toLocaleString(locale, {
      style: 'currency', currency: 'EUR', maximumFractionDigits: 0,
    });
  }

  // ── Data loading ──────────────────────────────────────────────────────
  private filters(): QuotaOverrideFilters {
    return {
      search: this.search() || undefined,
      targetType: this.targetTypeFilter() || undefined,
      kind: this.kindFilter() || undefined,
      status: this.statusFilter() || undefined,
      expiry: this.expiryFilter() || undefined,
    };
  }

  loadRows() {
    this.loading.set(true);
    this.error.set('');
    this.quotaService.list(this.filters(), this.currentPage(), this.pageSize).subscribe({
      next: page => {
        this.rows.set(page.content);
        this.totalElements.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.rows.set([]);
        this.loading.set(false);
        this.error.set(this.i18n.t('qov_load_failed'));
      },
    });
  }

  loadKpis() {
    this.quotaService.kpis().subscribe({
      next: k => this.kpis.set(k),
      error: () => { /* KPI strip stays empty — non-blocking */ },
    });
  }

  private refresh() {
    this.loadKpis();
    this.loadRows();
  }

  // ── Filters / search ─────────────────────────────────────────────────────
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  onSearch(event: Event) {
    this.search.set((event.target as HTMLInputElement).value);
    if (this.searchTimer) this.timers.clear(this.searchTimer);
    this.searchTimer = this.timers.set(() => {
      this.currentPage.set(0);
      this.loadRows();
    }, 400);
  }

  onTargetType(event: Event) {
    this.targetTypeFilter.set((event.target as HTMLSelectElement).value as '' | QuotaTargetType);
    this.currentPage.set(0);
    this.loadRows();
  }

  onKind(event: Event) {
    this.kindFilter.set((event.target as HTMLSelectElement).value as '' | QuotaOverrideKind);
    this.currentPage.set(0);
    this.loadRows();
  }

  onStatus(event: Event) {
    this.statusFilter.set((event.target as HTMLSelectElement).value as '' | 'active' | 'expired');
    this.currentPage.set(0);
    this.loadRows();
  }

  onExpiry(event: Event) {
    this.expiryFilter.set((event.target as HTMLSelectElement).value as '' | 'next7' | 'next30');
    this.currentPage.set(0);
    this.loadRows();
  }

  clearFilters() {
    this.search.set('');
    this.targetTypeFilter.set('');
    this.kindFilter.set('');
    this.statusFilter.set('');
    this.expiryFilter.set('');
    this.currentPage.set(0);
    this.loadRows();
  }

  goToPage(page: number) {
    this.currentPage.set(page);
    this.loadRows();
  }

  // ── Row helpers ──────────────────────────────────────────────────────────
  initial(name: string): string {
    return (name?.trim()?.[0] ?? '?').toUpperCase();
  }

  avatarHue(name: string): number {
    const s = name ?? '';
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
    return h;
  }

  /** Mini-bar fill width (0–100%, clamped). */
  capFillPct(r: QuotaOverride): number {
    if (r.effectiveCapCents == null) return 100;
    if (!r.effectiveCapCents) return 0;
    return Math.min((r.currentSpendCents / r.effectiveCapCents) * 100, 100);
  }

  /** Mini-bar tone: green ≤80%, amber >80%, danger >90%. Unlimited → accent. */
  capTone(r: QuotaOverride): string {
    if (r.effectiveCapCents == null) return 'unlim';
    if (!r.effectiveCapCents) return 'green';
    const ratio = r.currentSpendCents / r.effectiveCapCents;
    if (ratio > 0.9) return 'danger';
    if (ratio > 0.8) return 'amber';
    return 'green';
  }

  /** Whether a CAP override raised (vs lowered) the base cap. */
  capRaised(r: QuotaOverride): boolean {
    return (r.effectiveCapCents ?? 0) >= r.baseCapCents;
  }

  // ── Modal: open / close ───────────────────────────────────────────────────
  openCreate() {
    if (!this.identity.has('QUOTA_OVERRIDE')) return;
    this.resetForm();
    this.modalMode.set('create');
    this.editing.set(null);
    this.modalOpen.set(true);
  }

  openView(r: QuotaOverride) {
    this.seedForm(r);
    this.modalMode.set('view');
    this.editing.set(r);
    this.modalOpen.set(true);
  }

  openEdit(r: QuotaOverride) {
    if (!this.identity.has('QUOTA_OVERRIDE')) return;
    this.seedForm(r);
    this.modalMode.set('edit');
    this.editing.set(r);
    this.modalOpen.set(true);
  }

  /** From within a view modal, switch into edit mode (gated). */
  enterEdit() {
    const r = this.editing();
    if (!r || !this.identity.has('QUOTA_OVERRIDE')) return;
    this.modalMode.set('edit');
  }

  closeModal() {
    this.modalOpen.set(false);
    this.pickerOpen.set(false);
    this.modalError.set('');
  }

  private resetForm() {
    this.target.set(null);
    this.formKind.set('CREDIT');
    this.amount.set('');
    this.expiryDate.set('');
    this.noExpiry.set(false);
    this.reason.set('');
    this.attempted.set(false);
    this.modalError.set('');
    this.pickerQuery.set('');
    this.pickerResults.set([]);
    this.pickerOpen.set(false);
  }

  private seedForm(r: QuotaOverride) {
    this.resetForm();
    this.target.set({
      type: r.targetType, id: r.targetId, name: r.targetName,
      slug: r.targetEmailOrSlug, plan: r.basePlan, baseCapCents: r.baseCapCents,
    });
    this.formKind.set(r.kind);
    if (r.kind === 'CREDIT') this.amount.set(String(Math.round((r.amountCents ?? 0) / 100)));
    else if (r.kind === 'CAP') this.amount.set(String(Math.round((r.effectiveCapCents ?? 0) / 100)));
    else this.amount.set('');
    this.expiryDate.set(r.expiresAt ? r.expiresAt.slice(0, 10) : '');
    this.noExpiry.set(!r.expiresAt);
    this.reason.set(r.reason ?? '');
  }

  // ── Modal: form interactions ─────────────────────────────────────────────
  setKind(kind: QuotaOverrideKind) {
    if (this.isView()) return;
    this.formKind.set(kind);
  }

  onAmount(event: Event) {
    this.amount.set((event.target as HTMLInputElement).value);
  }

  onExpiryDate(event: Event) {
    this.expiryDate.set((event.target as HTMLInputElement).value);
  }

  toggleNoExpiry(event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.noExpiry.set(checked);
    if (checked) this.expiryDate.set('');
  }

  onReason(event: Event) {
    this.reason.set((event.target as HTMLTextAreaElement).value);
  }

  /** Today as YYYY-MM-DD (min for the date input). */
  get todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  // ── Target picker ─────────────────────────────────────────────────────────
  onPickerInput(event: Event) {
    const q = (event.target as HTMLInputElement).value;
    this.pickerQuery.set(q);
    this.pickerOpen.set(true);
    if (this.pickerTimer) this.timers.clear(this.pickerTimer);
    this.pickerTimer = this.timers.set(() => this.searchTargets(q), 300);
  }

  focusPicker() {
    if (this.targetLocked()) return;
    this.pickerOpen.set(true);
    if (this.pickerResults().length === 0 && !this.pickerLoading()) {
      this.searchTargets(this.pickerQuery());
    }
  }

  /** Searches users + orgs in parallel and merges into one list (orgs first, then users). */
  private searchTargets(q: string) {
    this.pickerLoading.set(true);
    let usersDone = false;
    let orgsDone = false;
    let users: TargetCandidate[] = [];
    let orgs: TargetCandidate[] = [];

    const finish = () => {
      if (usersDone && orgsDone) {
        this.pickerResults.set([...orgs, ...users]);
        this.pickerLoading.set(false);
      }
    };

    this.orgService.list(q, undefined, 0, 6).subscribe({
      next: page => {
        orgs = page.content
          .filter(o => !o.personal)
          .map(o => ({ type: 'ORG' as const, id: o.id, name: o.name, slug: o.slug || o.id, plan: o.planName }));
        orgsDone = true; finish();
      },
      error: () => { orgsDone = true; finish(); },
    });

    this.adminService.getUsers(q, undefined, undefined, undefined, 0, 6).subscribe({
      next: page => {
        users = page.content
          .map(u => ({ type: 'USER' as const, id: u.id, name: u.fullName || u.email, slug: u.email, plan: u.planName }));
        usersDone = true; finish();
      },
      error: () => { usersDone = true; finish(); },
    });
  }

  pickTarget(c: TargetCandidate) {
    this.target.set({ ...c });
    this.pickerOpen.set(false);
    this.pickerQuery.set('');
  }

  clearTarget() {
    if (this.targetLocked()) return;
    this.target.set(null);
    this.pickerResults.set([]);
    this.pickerOpen.set(false);
  }

  // ── Save ──────────────────────────────────────────────────────────────────
  save() {
    if (this.isView() || !this.identity.has('QUOTA_OVERRIDE') || this.saving()) return;
    this.attempted.set(true);
    if (this.targetMissing() || this.amountMissing() || this.reasonMissing()) return;

    const t = this.target()!;
    const kind = this.formKind();
    const req: QuotaOverrideRequest = {
      targetType: t.type,
      targetId: t.id,
      kind,
      amountCents: kind === 'UNLIMITED' ? null : this.amountCents(),
      expiresAt: this.noExpiry() || !this.expiryDate() ? null : this.expiryDate(),
      reason: this.reason().trim(),
    };

    this.saving.set(true);
    this.modalError.set('');
    const editingRow = this.editing();
    const obs = editingRow
      ? this.quotaService.update(editingRow.id, req)
      : this.quotaService.create(req);

    obs.subscribe({
      next: () => {
        this.saving.set(false);
        this.flashMsg(editingRow
          ? this.i18n.t('qov_flash_updated')
          : this.i18n.t('qov_flash_created', { name: t.name }));
        this.closeModal();
        this.refresh();
      },
      error: () => {
        this.saving.set(false);
        this.modalError.set(this.i18n.t('qov_action_failed'));
      },
    });
  }

  // ── Revoke ──────────────────────────────────────────────────────────────
  async revoke(r: QuotaOverride, event?: Event) {
    event?.stopPropagation();
    if (!this.identity.has('QUOTA_OVERRIDE')) return;
    const kindLabel = this.i18n.t(
      r.kind === 'CREDIT' ? 'qov_kind_credit' : r.kind === 'CAP' ? 'qov_kind_cap' : 'qov_kind_unlimited');
    const confirmed = await this.confirmDialog.confirm({
      title: this.i18n.t('qov_revoke_title'),
      message: this.i18n.t('qov_revoke_msg', { kind: kindLabel.toLowerCase(), name: r.targetName, plan: r.basePlan }),
      confirmText: this.i18n.t('qov_revoke_confirm'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!confirmed) return;
    this.quotaService.revoke(r.id).subscribe({
      next: () => {
        this.rows.update(list => list.filter(x => x.id !== r.id));
        this.flashMsg(this.i18n.t('qov_flash_revoked', { name: r.targetName }));
        this.refresh();
      },
      error: () => this.error.set(this.i18n.t('qov_action_failed')),
    });
  }

  // ── Row menu (fixed-positioned so it escapes the table's overflow clipping) ──
  openMenuId = signal<string | null>(null);
  menuPos = signal<{ top: number; left: number } | null>(null);

  toggleMenu(id: string, event: Event) {
    event.stopPropagation();
    if (this.openMenuId() === id) { this.openMenuId.set(null); return; }
    const btn = event.currentTarget as HTMLElement;
    const r = btn.getBoundingClientRect();
    this.menuPos.set({ top: r.bottom + 4, left: Math.max(8, r.right - 160) });
    this.openMenuId.set(id);
  }

  @HostListener('document:click')
  onDocClick() {
    this.openMenuId.set(null);
    // Close the picker dropdown on any outside click (re-opened via focus).
    if (this.pickerOpen()) this.pickerOpen.set(false);
  }

  @HostListener('window:scroll')
  @HostListener('window:resize')
  onViewportChange() {
    this.openMenuId.set(null);
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.modalOpen()) this.closeModal();
    this.openMenuId.set(null);
  }

  private flashMsg(msg: string) {
    this.flash.set(msg);
    if (this.flashTimer) this.timers.clear(this.flashTimer);
    this.flashTimer = this.timers.set(() => this.flash.set(''), 3200);
  }
}
