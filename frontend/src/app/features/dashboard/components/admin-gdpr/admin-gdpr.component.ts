import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminGdprService } from '../../../../core/services/admin-gdpr.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import {
  GdprRequest,
  GdprRequestDetail,
  GdprKpis,
  GdprRetention,
  GdprType,
  GdprStatus,
  GdprChannel,
  DeadlineState,
  GdprFilters,
  CreateGdprRequest,
} from '../../../../models/admin-gdpr.model';

type DetailTab = 'overview' | 'footprint' | 'timeline';
const DAY = 86_400_000;

/**
 * Platform-staff GDPR / Data Requests (DSAR) console. Compliance staff handle export / erasure /
 * rectification / restriction / access requests against the statutory 30-day deadline, see each
 * subject's data footprint, and run the gated mutations. Read COMPLIANCE_VIEW, mutate COMPLIANCE_MANAGE.
 */
@Component({
  selector: 'app-admin-gdpr',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-gdpr.component.html',
  styleUrl: './admin-gdpr.component.scss',
})
export class AdminGdprComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private service = inject(AdminGdprService);
  private confirmDialog = inject(ConfirmDialogService);
  private router = inject(Router);
  private timers = managedTimers();

  // ── List state ────────────────────────────────────────────────────────────
  rows = signal<GdprRequest[]>([]);
  total = signal(0);
  totalPages = signal(0);
  page = signal(0);
  loading = signal(true);
  error = signal('');
  kpis = signal<GdprKpis | null>(null);
  retention = signal<GdprRetention | null>(null);
  refreshing = signal(false);
  updatedAt = signal('');
  alertDismissed = signal(false);
  busy = signal(false);
  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Filters / sort ──────────────────────────────────────────────────────────
  search = signal('');
  typeF = signal<'' | GdprType>('');
  statusF = signal<'' | GdprStatus>('');
  deadlineF = signal<'' | 'overdue' | 'due-soon'>('');
  sortKey = signal<'requested' | 'deadline' | 'status'>('deadline');
  sortDir = signal<'asc' | 'desc'>('asc');

  // ── Detail modal ──────────────────────────────────────────────────────────
  detail = signal<GdprRequestDetail | null>(null);
  detailLoading = signal(false);
  detailTab = signal<DetailTab>('overview');
  detailFlash = signal('');
  private detailFlashTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Reject + create modals ──────────────────────────────────────────────────
  rejecting = signal<GdprRequest | null>(null);
  rejectReason = signal('');
  creating = signal(false);
  cForm = signal<CreateGdprRequest>({ subjectEmail: '', subjectName: '', type: 'EXPORT', channel: 'EMAIL', note: '' });

  readonly pageSize = 10;
  readonly stars = [1, 2, 3, 4, 5];
  readonly types: GdprType[] = ['EXPORT', 'ERASURE', 'RECTIFICATION', 'RESTRICTION', 'ACCESS'];
  readonly channels: GdprChannel[] = ['EMAIL', 'IN_APP', 'POST', 'PHONE'];
  readonly footprintKeys: { key: keyof GdprRequestDetail['footprint']; icon: string; labelKey: string }[] = [
    { key: 'mailboxes', icon: 'mailbox', labelKey: 'gdpr_fp_mailboxes' },
    { key: 'emails', icon: 'mail', labelKey: 'gdpr_fp_emails' },
    { key: 'automations', icon: 'automations', labelKey: 'gdpr_fp_automations' },
    { key: 'conversations', icon: 'sparkle', labelKey: 'gdpr_fp_conversations' },
    { key: 'auditEntries', icon: 'list', labelKey: 'gdpr_fp_audit' },
  ];

  readonly canManage = computed(() => this.identity.has('COMPLIANCE_MANAGE'));
  readonly hasFilters = computed(() => !!this.search() || !!this.typeF() || !!this.statusF() || !!this.deadlineF());
  readonly banner = computed<{ kind: 'danger' | 'warn'; n: number } | null>(() => {
    const k = this.kpis();
    if (!k) return null;
    if (k.overdue > 0) return { kind: 'danger', n: k.overdue };
    if (k.dueSoon > 0) return { kind: 'warn', n: k.dueSoon };
    return null;
  });
  readonly cFormValid = computed(() => {
    const f = this.cForm();
    return /\S+@\S+\.\S+/.test(f.subjectEmail) && f.subjectName.trim().length > 0;
  });

  ngOnInit() {
    this.loadKpis();
    this.loadRetention();
    this.stampUpdated();
    this.loadRequests();
  }

  // ── Loading ──────────────────────────────────────────────────────────────────
  private filters(): GdprFilters {
    return {
      search: this.search() || undefined, type: this.typeF() || undefined, status: this.statusF() || undefined,
      deadline: this.deadlineF() || undefined, sort: this.sortKey(), dir: this.sortDir(),
    };
  }
  loadRequests() {
    this.loading.set(true);
    this.error.set('');
    this.service.requests(this.filters(), this.page(), this.pageSize).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.totalElements); this.totalPages.set(p.totalPages); this.loading.set(false); },
      error: () => { this.rows.set([]); this.loading.set(false); this.error.set(this.i18n.t('gdpr_load_failed')); },
    });
  }
  loadKpis() { this.service.kpis().subscribe({ next: k => this.kpis.set(k), error: () => {} }); }
  loadRetention() { this.service.retention().subscribe({ next: r => this.retention.set(r), error: () => {} }); }

  private stampUpdated() {
    const d = new Date(); const p = (n: number) => String(n).padStart(2, '0');
    this.updatedAt.set(`${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`);
  }
  refresh() {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    this.loadKpis(); this.loadRetention(); this.loadRequests(); this.stampUpdated();
    this.timers.set(() => this.refreshing.set(false), 600);
  }

  // ── Filters / sort ───────────────────────────────────────────────────────────
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  onSearch(e: Event) { this.search.set((e.target as HTMLInputElement).value); if (this.searchTimer) this.timers.clear(this.searchTimer); this.searchTimer = this.timers.set(() => { this.page.set(0); this.loadRequests(); }, 400); }
  onType(e: Event) { this.typeF.set((e.target as HTMLSelectElement).value as '' | GdprType); this.page.set(0); this.loadRequests(); }
  onStatus(e: Event) { this.statusF.set((e.target as HTMLSelectElement).value as '' | GdprStatus); this.page.set(0); this.loadRequests(); }
  onDeadline(e: Event) { this.deadlineF.set((e.target as HTMLSelectElement).value as '' | 'overdue' | 'due-soon'); this.page.set(0); this.loadRequests(); }
  toggleSort(key: 'requested' | 'deadline' | 'status') {
    if (this.sortKey() === key) this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    else { this.sortKey.set(key); this.sortDir.set('asc'); }
    this.page.set(0); this.loadRequests();
  }
  clearFilters() { this.search.set(''); this.typeF.set(''); this.statusF.set(''); this.deadlineF.set(''); this.page.set(0); this.loadRequests(); }
  goToPage(p: number) { if (p < 0 || p >= this.totalPages()) return; this.page.set(p); this.loadRequests(); }
  inspectBanner() { const b = this.banner(); if (!b) return; this.deadlineF.set(b.kind === 'danger' ? 'overdue' : 'due-soon'); this.statusF.set(''); this.page.set(0); this.loadRequests(); }
  openRetentionJob() { this.router.navigate(['/dashboard/admin/jobs']); }

  // ── Display helpers ──────────────────────────────────────────────────────────
  initial(name: string): string { return (name?.trim()?.[0] ?? '?').toUpperCase(); }
  avatarHue(seed: string): number { const s = seed ?? ''; let h = 0; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360; return h; }
  isOpen(r: GdprRequest): boolean { return r.status === 'PENDING' || r.status === 'IN_PROGRESS'; }
  daysLeft(r: GdprRequest): number { return Math.ceil((new Date(r.deadlineAt).getTime() - Date.now()) / DAY); }
  deadlineState(r: GdprRequest): DeadlineState { const l = this.daysLeft(r); return l < 0 ? 'overdue' : l <= 7 ? 'due-soon' : 'ok'; }
  deadlineDotState(r: GdprRequest): string { const s = this.deadlineState(r); return s === 'overdue' ? 'down' : s === 'due-soon' ? 'warn' : 'ok'; }
  deadlineLabel(r: GdprRequest): string {
    if (!this.isOpen(r)) return this.i18n.t('gdpr_closed_lc');
    const l = this.daysLeft(r);
    if (l < 0) return this.i18n.t('gdpr_overdue_days', { n: Math.abs(l) + '' });
    if (l === 0) return this.i18n.t('gdpr_due_today');
    return this.i18n.t('gdpr_days_left', { n: l + '' });
  }
  typeTone(t: GdprType): string { return t === 'EXPORT' ? 'violet' : t === 'ERASURE' ? 'danger' : t === 'RECTIFICATION' ? 'amber' : t === 'RESTRICTION' ? 'slate' : 'green'; }
  typeIcon(t: GdprType): string { return t === 'EXPORT' ? 'download' : t === 'ERASURE' ? 'trash' : t === 'RECTIFICATION' ? 'edit' : t === 'RESTRICTION' ? 'lock' : 'eye'; }
  typeLabel(t: GdprType): string { return this.i18n.t('gdpr_type_' + t.toLowerCase()); }
  statusTone(s: GdprStatus): string { return s === 'COMPLETED' ? 'green' : s === 'IN_PROGRESS' ? 'violet' : s === 'REJECTED' ? 'slate' : 'amber'; }
  statusDot(s: GdprStatus): string { return s === 'COMPLETED' ? 'ok' : s === 'REJECTED' ? 'slate' : 'warn'; }
  statusLabel(s: GdprStatus): string { return this.i18n.t('gdpr_status_' + s.toLowerCase().replace('_', '')); }
  channelLabel(c: GdprChannel): string { return this.i18n.t('gdpr_channel_' + c.toLowerCase().replace('_', '')); }
  deadlineChipTone(r: GdprRequest): string { return !this.isOpen(r) ? 'slate' : this.deadlineState(r) === 'overdue' ? 'danger' : this.deadlineState(r) === 'due-soon' ? 'amber' : 'slate'; }
  relative(at: string | null): string {
    if (!at) return '—';
    const days = Math.floor((Date.now() - new Date(at).getTime()) / DAY);
    if (days <= 0) return this.i18n.t('gdpr_ago_today');
    if (days === 1) return this.i18n.t('gdpr_ago_yesterday');
    if (days < 30) return this.i18n.t('gdpr_ago_days', { n: days + '' });
    return this.i18n.t('gdpr_ago_months', { n: Math.round(days / 30) + '' });
  }
  retLastSweep(): string {
    const at = this.retention()?.lastSweepAt;
    return at ? this.relative(at) : this.i18n.t('gdpr_ret_never');
  }

  // ── Detail modal ────────────────────────────────────────────────────────────
  openRequest(r: GdprRequest) {
    this.detailTab.set('overview');
    this.detailFlash.set('');
    this.detailLoading.set(true);
    this.detail.set({ request: r, footprint: { mailboxes: 0, emails: 0, automations: 0, conversations: 0, auditEntries: 0 }, timeline: [] });
    this.service.getRequest(r.id).subscribe({
      next: d => { this.detail.set(d); this.detailLoading.set(false); },
      error: () => { this.detailLoading.set(false); },
    });
  }
  closeDetail() { this.detail.set(null); }
  setDetailTab(t: DetailTab) { this.detailTab.set(t); }
  detailClosed(): boolean { const r = this.detail()?.request; return !!r && (r.status === 'COMPLETED' || r.status === 'REJECTED'); }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.rejecting()) { this.rejecting.set(null); return; }
    if (this.creating()) { this.creating.set(false); return; }
    if (this.detail()) this.closeDetail();
  }
  @HostListener('document:click')
  onDocClick() { this.openMenuId.set(null); }
  @HostListener('window:scroll')
  @HostListener('window:resize')
  onViewport() { this.openMenuId.set(null); }

  // ── Row menu ──────────────────────────────────────────────────────────────────
  openMenuId = signal<string | null>(null);
  menuPos = signal<{ top: number; left: number } | null>(null);
  toggleMenu(id: string, event: Event) {
    event.stopPropagation();
    if (this.openMenuId() === id) { this.openMenuId.set(null); return; }
    const r = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.menuPos.set({ top: r.bottom + 4, left: Math.max(8, r.right - 192) });
    this.openMenuId.set(id);
  }

  // ── Actions ───────────────────────────────────────────────────────────────────
  private flashMsg(m: string) { this.flash.set(m); if (this.flashTimer) this.timers.clear(this.flashTimer); this.flashTimer = this.timers.set(() => this.flash.set(''), 3200); }
  private detailFlashMsg(m: string) { this.detailFlash.set(m); if (this.detailFlashTimer) this.timers.clear(this.detailFlashTimer); this.detailFlashTimer = this.timers.set(() => this.detailFlash.set(''), 2600); }
  private applyRequest(u: GdprRequest) {
    this.rows.update(list => list.map(x => x.id === u.id ? u : x));
    if (this.detail()?.request.id === u.id) this.detail.update(d => d ? { ...d, request: u } : d);
    this.loadKpis();
  }

  runExport(r: GdprRequest, event?: Event) {
    event?.stopPropagation();
    if (!this.canManage() || this.busy()) return;
    this.busy.set(true);
    this.service.runExport(r.id).subscribe({
      next: u => { this.busy.set(false); this.applyRequest(u); const m = this.i18n.t('gdpr_flash_export', { name: r.subjectName }); this.flashMsg(m); this.detailFlashMsg(m); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('gdpr_action_failed')); },
    });
  }
  async executeErasure(r: GdprRequest, event?: Event) {
    event?.stopPropagation();
    if (!this.canManage() || this.busy()) return;
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('gdpr_erase_title', { name: r.subjectName }),
      message: this.i18n.t('gdpr_erase_msg'),
      confirmText: this.i18n.t('gdpr_erase_confirm'), cancelText: this.i18n.t('confirm_cancel'), tone: 'danger',
    });
    if (!ok) return;
    this.busy.set(true);
    this.service.executeErasure(r.id).subscribe({
      next: u => { this.busy.set(false); this.applyRequest(u); const m = this.i18n.t('gdpr_flash_erase', { name: r.subjectName }); this.flashMsg(m); this.detailFlashMsg(m); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('gdpr_action_failed')); },
    });
  }
  markComplete(r: GdprRequest, event?: Event) {
    event?.stopPropagation();
    if (!this.canManage() || this.busy()) return;
    this.busy.set(true);
    this.service.markComplete(r.id).subscribe({
      next: u => { this.busy.set(false); this.applyRequest(u); const m = this.i18n.t('gdpr_flash_complete', { id: r.id }); this.flashMsg(m); this.detailFlashMsg(m); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('gdpr_action_failed')); },
    });
  }

  // Reject (reason dialog)
  openReject(r: GdprRequest, event?: Event) { event?.stopPropagation(); if (!this.canManage()) return; this.rejectReason.set(''); this.rejecting.set(r); }
  closeReject() { this.rejecting.set(null); }
  onRejectReason(e: Event) { this.rejectReason.set((e.target as HTMLTextAreaElement).value); }
  confirmReject() {
    const r = this.rejecting(); const reason = this.rejectReason().trim();
    if (!r || !reason || this.busy()) return;
    this.busy.set(true);
    this.service.reject(r.id, reason).subscribe({
      next: u => { this.busy.set(false); this.rejecting.set(null); this.applyRequest(u); const m = this.i18n.t('gdpr_flash_reject', { id: r.id }); this.flashMsg(m); this.detailFlashMsg(m); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('gdpr_action_failed')); },
    });
  }

  // Create (New request)
  openCreate() { if (!this.canManage()) return; this.cForm.set({ subjectEmail: '', subjectName: '', type: 'EXPORT', channel: 'EMAIL', note: '' }); this.creating.set(true); }
  closeCreate() { this.creating.set(false); }
  patchForm<K extends keyof CreateGdprRequest>(key: K, e: Event) { const v = (e.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value; this.cForm.update(f => ({ ...f, [key]: v })); }
  submitCreate() {
    if (!this.cFormValid() || this.busy()) return;
    const f = this.cForm();
    this.busy.set(true);
    this.service.create({ ...f, subjectEmail: f.subjectEmail.trim(), subjectName: f.subjectName.trim(), note: f.note.trim() }).subscribe({
      next: () => { this.busy.set(false); this.creating.set(false); this.page.set(0); this.loadRequests(); this.loadKpis(); this.flashMsg(this.i18n.t('gdpr_flash_created')); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('gdpr_action_failed')); },
    });
  }
}
