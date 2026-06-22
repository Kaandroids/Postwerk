import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminEmailHealthService } from '../../../../core/services/admin-email-health.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import {
  MailboxHealthRow,
  MailboxHealthDetail,
  EmailHealthKpis,
  EmailClusterSummary,
  EmailHealthFilters,
  MailboxHealth,
} from '../../../../models/admin-email-health.model';

type SortKey = 'email' | 'health' | 'sync';
type SortDir = 'asc' | 'desc';
type ModalTab = 'overview' | 'attempts';

/**
 * Platform-staff Email Health: alert strip + KPI strip + by-cluster summary + filterable/paginated
 * mailbox table + centered detail modal, with gated re-sync / pause / resume (INFRA_MANAGE). Reads
 * need INFRA_VIEW. Mutations are UX-gated only — the backend enforces every action.
 */
@Component({
  selector: 'app-admin-email-health',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-email-health.component.html',
  styleUrl: './admin-email-health.component.scss',
})
export class AdminEmailHealthComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private service = inject(AdminEmailHealthService);
  private confirmDialog = inject(ConfirmDialogService);
  private timers = managedTimers();

  // ── List state ────────────────────────────────────────────────────────────
  rows = signal<MailboxHealthRow[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  currentPage = signal(0);
  readonly pageSize = 10;
  loading = signal(true);
  error = signal('');

  kpis = signal<EmailHealthKpis | null>(null);
  clusters = signal<EmailClusterSummary[]>([]);

  refreshing = signal(false);
  updatedAt = signal('');
  alertDismissed = signal(false);

  // ── Filters ─────────────────────────────────────────────────────────────────
  search = signal('');
  protocolFilter = signal<'' | 'IMAP' | 'SMTP'>('');
  healthFilter = signal<'' | MailboxHealth | 'paused'>('');
  serverFilter = signal('');
  syncFilter = signal<'' | 'recent' | 'stale'>('');

  sortKey = signal<SortKey>('health');
  sortDir = signal<SortDir>('desc');

  hasActiveFilters = computed(() =>
    !!this.search() || !!this.protocolFilter() || !!this.healthFilter() ||
    !!this.serverFilter() || !!this.syncFilter());

  /** The first down cluster drives the danger alert strip. */
  downCluster = computed(() => this.clusters().find(c => c.status === 'down') ?? null);

  /** Client-side sort over the current page (server returns severity-desc; backend handles paging). */
  readonly sortedRows = computed(() => {
    const list = [...this.rows()];
    const dir = this.sortDir() === 'asc' ? 1 : -1;
    const rank = (h: MailboxHealth) => (h === 'failing' ? 2 : h === 'auth_error' ? 1 : 0);
    switch (this.sortKey()) {
      case 'email': list.sort((a, b) => a.email.localeCompare(b.email) * dir); break;
      case 'health': list.sort((a, b) => (rank(a.health) - rank(b.health)) * dir); break;
      case 'sync': list.sort((a, b) => ((a.syncAgoMinutes ?? -1) - (b.syncAgoMinutes ?? -1)) * dir); break;
    }
    return list;
  });

  // ── Flash ─────────────────────────────────────────────────────────────────
  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Detail modal ────────────────────────────────────────────────────────────
  detailOpen = signal(false);
  detail = signal<MailboxHealthDetail | null>(null);
  detailLoading = signal(false);
  modalTab = signal<ModalTab>('overview');
  busy = signal(false);

  ngOnInit() {
    this.refreshAll();
  }

  // ── Data loading ──────────────────────────────────────────────────────────
  private filters(): EmailHealthFilters {
    return {
      search: this.search() || undefined,
      protocol: this.protocolFilter() || undefined,
      health: this.healthFilter() || undefined,
      server: this.serverFilter() || undefined,
      sync: this.syncFilter() || undefined,
    };
  }

  loadRows() {
    this.loading.set(true);
    this.error.set('');
    this.service.listMailboxes(this.filters(), this.currentPage(), this.pageSize).subscribe({
      next: page => {
        this.rows.set(page.content);
        this.totalElements.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.rows.set([]);
        this.loading.set(false);
        this.error.set(this.i18n.t('eh_load_failed'));
      },
    });
  }

  loadKpis() {
    this.service.kpis().subscribe({
      next: k => this.kpis.set(k),
      error: () => { /* KPI strip stays empty — non-blocking */ },
    });
  }

  loadClusters() {
    this.service.clusters().subscribe({
      next: c => this.clusters.set(c),
      error: () => { /* cluster row stays empty — non-blocking */ },
    });
  }

  private stampUpdated() {
    const d = new Date();
    const p = (n: number) => String(n).padStart(2, '0');
    this.updatedAt.set(`${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`);
  }

  private refreshAll() {
    this.loadKpis();
    this.loadClusters();
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

  onProtocol(event: Event) {
    this.protocolFilter.set((event.target as HTMLSelectElement).value as '' | 'IMAP' | 'SMTP');
    this.currentPage.set(0); this.loadRows();
  }

  onHealth(event: Event) {
    this.healthFilter.set((event.target as HTMLSelectElement).value as '' | MailboxHealth | 'paused');
    this.currentPage.set(0); this.loadRows();
  }

  onServer(event: Event) {
    this.serverFilter.set((event.target as HTMLSelectElement).value);
    this.currentPage.set(0); this.loadRows();
  }

  onSync(event: Event) {
    this.syncFilter.set((event.target as HTMLSelectElement).value as '' | 'recent' | 'stale');
    this.currentPage.set(0); this.loadRows();
  }

  clearFilters() {
    this.search.set('');
    this.protocolFilter.set('');
    this.healthFilter.set('');
    this.serverFilter.set('');
    this.syncFilter.set('');
    this.currentPage.set(0);
    this.loadRows();
  }

  /** Cluster card click toggles it as the active server filter; the alert CTA sets it. */
  toggleCluster(host: string) {
    this.serverFilter.set(this.serverFilter() === host ? '' : host);
    this.currentPage.set(0);
    this.loadRows();
  }

  inspectCluster(host: string) {
    this.serverFilter.set(host);
    this.alertDismissed.set(true);
    this.currentPage.set(0);
    this.loadRows();
  }

  toggleSort(key: SortKey) {
    if (this.sortKey() === key) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortKey.set(key);
      this.sortDir.set(key === 'email' ? 'asc' : 'desc');
    }
  }

  goToPage(page: number) {
    if (page < 0 || page >= this.totalPages()) return;
    this.currentPage.set(page);
    this.loadRows();
  }

  // ── Display helpers ──────────────────────────────────────────────────────────
  initial(name: string): string {
    return (name?.trim()?.[0] ?? '?').toUpperCase();
  }

  avatarHue(seed: string): number {
    const s = seed ?? '';
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
    return h;
  }

  /** Humanizes minutes-ago: null → never; <1 → now; <60 → Nm; <1440 → Nh; else Nd. */
  ago(min: number | null | undefined): string {
    if (min == null) return this.i18n.t('eh_never');
    if (min < 1) return this.i18n.t('eh_just_now');
    if (min < 60) return `${Math.round(min)}m`;
    if (min < 1440) return `${Math.round(min / 60)}h`;
    return `${Math.round(min / 1440)}d`;
  }

  /** Tone for a mailbox's health (paused outranks the underlying health visually). */
  healthTone(row: { health: MailboxHealth; paused: boolean }): string {
    if (row.paused) return 'amber';
    return row.health === 'failing' ? 'danger' : row.health === 'auth_error' ? 'amber' : 'green';
  }

  healthLabel(row: { health: MailboxHealth; paused: boolean }): string {
    if (row.paused) return this.i18n.t('eh_status_paused');
    return this.i18n.t(
      row.health === 'failing' ? 'eh_status_failing'
        : row.health === 'auth_error' ? 'eh_status_auth'
          : 'eh_status_ok');
  }

  /** ok/warn/down dot class for a cluster. */
  clusterDot(status: string): string {
    return status === 'down' ? 'down' : status === 'warn' ? 'warn' : 'ok';
  }

  // ── Detail modal ────────────────────────────────────────────────────────────
  openDetail(row: MailboxHealthRow) {
    this.modalTab.set('overview');
    this.error.set('');
    this.detailLoading.set(true);
    this.detail.set(null);
    this.detailOpen.set(true);
    this.service.getMailbox(row.id).subscribe({
      next: d => { this.detail.set(d); this.detailLoading.set(false); },
      error: () => { this.detailLoading.set(false); this.error.set(this.i18n.t('eh_load_failed')); },
    });
  }

  closeDetail() {
    this.detailOpen.set(false);
    this.detail.set(null);
  }

  setTab(tab: ModalTab) { this.modalTab.set(tab); }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.detailOpen()) this.closeDetail();
    this.openMenuId.set(null);
  }

  // ── Row menu (fixed-positioned so it escapes the table's overflow clipping) ──
  openMenuId = signal<string | null>(null);
  menuPos = signal<{ top: number; left: number } | null>(null);

  toggleMenu(id: string, event: Event) {
    event.stopPropagation();
    if (this.openMenuId() === id) { this.openMenuId.set(null); return; }
    const btn = event.currentTarget as HTMLElement;
    const r = btn.getBoundingClientRect();
    // Right-align the menu under the trigger; clamp to the viewport.
    this.menuPos.set({ top: r.bottom + 4, left: Math.max(8, r.right - 184) });
    this.openMenuId.set(id);
  }

  @HostListener('document:click')
  onDocClick() {
    this.openMenuId.set(null);
  }

  @HostListener('window:scroll')
  @HostListener('window:resize')
  onViewportChange() {
    this.openMenuId.set(null);
  }

  // ── Actions (INFRA_MANAGE) ──────────────────────────────────────────────────
  private flashMsg(msg: string) {
    this.flash.set(msg);
    if (this.flashTimer) this.timers.clear(this.flashTimer);
    this.flashTimer = this.timers.set(() => this.flash.set(''), 3200);
  }

  resync(row: { id: string; email: string }, event?: Event) {
    event?.stopPropagation();
    if (!this.identity.has('INFRA_MANAGE') || this.busy()) return;
    this.busy.set(true);
    this.service.resync(row.id).subscribe({
      next: () => { this.busy.set(false); this.flashMsg(this.i18n.t('eh_flash_resync', { email: row.email })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('eh_action_failed')); },
    });
  }

  async pause(row: { id: string; email: string }, event?: Event) {
    event?.stopPropagation();
    if (!this.identity.has('INFRA_MANAGE')) return;
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('eh_pause_title'),
      message: this.i18n.t('eh_pause_msg', { email: row.email }),
      confirmText: this.i18n.t('eh_pause'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!ok) return;
    this.busy.set(true);
    this.service.pause(row.id).subscribe({
      next: d => { this.busy.set(false); this.applyMutation(d); this.flashMsg(this.i18n.t('eh_flash_paused', { email: row.email })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('eh_action_failed')); },
    });
  }

  resume(row: { id: string; email: string }, event?: Event) {
    event?.stopPropagation();
    if (!this.identity.has('INFRA_MANAGE') || this.busy()) return;
    this.busy.set(true);
    this.service.resume(row.id).subscribe({
      next: d => { this.busy.set(false); this.applyMutation(d); this.flashMsg(this.i18n.t('eh_flash_resumed', { email: row.email })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('eh_action_failed')); },
    });
  }

  /** Reflects a pause/resume into the open detail + the list row + refreshed KPIs. */
  private applyMutation(d: MailboxHealthDetail) {
    if (this.detail()?.id === d.id) this.detail.set(d);
    this.rows.update(list => list.map(r => r.id === d.id ? { ...r, paused: d.paused } : r));
    this.loadKpis();
  }
}
