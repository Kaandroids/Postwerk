import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminBackgroundJobsService } from '../../../../core/services/admin-background-jobs.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import {
  Job,
  JobDetail,
  JobQueue,
  JobStatus,
  BackgroundJobsKpis,
} from '../../../../models/admin-background-jobs.model';

type ModalTab = 'overview' | 'runs';
type SortKey = 'name' | 'lastRun' | 'nextRun' | 'status';

const KIND_ICON: Record<string, string> = { Scheduler: 'clock', Worker: 'refresh', Maintenance: 'shield' };
const SEVERITY: Record<JobStatus, number> = { failing: 2, paused: 1, healthy: 0 };

/**
 * Platform-staff Background Jobs: alert strip + 6-KPI strip + 2 queue cards + filterable jobs table
 * + centered job detail modal (Overview + Recent-runs), with gated run-now / pause / resume
 * (INFRA_MANAGE). Reads need INFRA_VIEW. Mutations are UX-gated only — the backend enforces them.
 */
@Component({
  selector: 'app-admin-background-jobs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-background-jobs.component.html',
  styleUrl: './admin-background-jobs.component.scss',
})
export class AdminBackgroundJobsComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private service = inject(AdminBackgroundJobsService);
  private confirmDialog = inject(ConfirmDialogService);
  private timers = managedTimers();

  jobs = signal<Job[]>([]);
  kpis = signal<BackgroundJobsKpis | null>(null);
  queues = signal<JobQueue[]>([]);

  loading = signal(true);
  error = signal('');
  refreshing = signal(false);
  updatedAt = signal('');
  alertDismissed = signal(false);

  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // Filters
  search = signal('');
  typeFilter = signal<'' | 'Scheduler' | 'Worker' | 'Maintenance'>('');
  statusFilter = signal<'' | JobStatus>('');
  lastRunFilter = signal<'' | 'succeeded' | 'failed' | 'never'>('');
  sortKey = signal<SortKey>('status');
  sortDir = signal<'asc' | 'desc'>('desc');

  hasActiveFilters = computed(() =>
    !!this.search() || !!this.typeFilter() || !!this.statusFilter() || !!this.lastRunFilter());

  // Detail modal
  detailOpen = signal(false);
  detail = signal<JobDetail | null>(null);
  detailLoading = signal(false);
  modalTab = signal<ModalTab>('overview');
  busy = signal(false);

  readonly canManage = computed(() => this.identity.has('INFRA_MANAGE'));

  /** First failing job (else a backed-up queue's drain job) drives the alert. */
  readonly alertJob = computed(() => this.jobs().find(j => j.status === 'failing') ?? null);
  readonly alertQueue = computed(() => this.queues().find(q => q.tone === 'backlog') ?? null);

  readonly filteredJobs = computed(() => {
    const q = this.search().trim().toLowerCase();
    let list = this.jobs().filter(j => {
      if (q && !(j.name.toLowerCase().includes(q) || j.id.toLowerCase().includes(q) || j.type.toLowerCase().includes(q))) return false;
      if (this.typeFilter() && j.type !== this.typeFilter()) return false;
      if (this.statusFilter() && j.status !== this.statusFilter()) return false;
      const lr = this.lastRunFilter();
      if (lr === 'succeeded' && j.lastRunOk !== true) return false;
      if (lr === 'failed' && j.lastRunOk !== false) return false;
      if (lr === 'never' && j.lastRunAt !== null) return false;
      return true;
    });
    const dir = this.sortDir() === 'asc' ? 1 : -1;
    list = [...list].sort((a, b) => {
      switch (this.sortKey()) {
        case 'name': return a.name.localeCompare(b.name) * dir;
        case 'lastRun': return ((a.lastRunAt ? Date.parse(a.lastRunAt) : 0) - (b.lastRunAt ? Date.parse(b.lastRunAt) : 0)) * dir;
        case 'nextRun': return ((a.nextRunAt ? Date.parse(a.nextRunAt) : Infinity) - (b.nextRunAt ? Date.parse(b.nextRunAt) : Infinity)) * dir;
        default: return (SEVERITY[a.status] - SEVERITY[b.status]) * dir;
      }
    });
    return list;
  });

  ngOnInit() { this.refreshAll(); }

  // ── Loading ──────────────────────────────────────────────────────────────
  loadJobs() {
    this.loading.set(true);
    this.error.set('');
    this.service.jobs().subscribe({
      next: j => { this.jobs.set(j); this.loading.set(false); },
      error: () => { this.jobs.set([]); this.loading.set(false); this.error.set(this.i18n.t('bj_load_failed')); },
    });
  }
  loadKpis() { this.service.kpis().subscribe({ next: k => this.kpis.set(k), error: () => {} }); }
  loadQueues() { this.service.queues().subscribe({ next: q => this.queues.set(q), error: () => this.queues.set([]) }); }

  private stampUpdated() {
    const d = new Date();
    const p = (n: number) => String(n).padStart(2, '0');
    this.updatedAt.set(`${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`);
  }
  private refreshAll() { this.loadKpis(); this.loadQueues(); this.loadJobs(); this.stampUpdated(); }
  refresh() { if (this.refreshing()) return; this.refreshing.set(true); this.refreshAll(); this.timers.set(() => this.refreshing.set(false), 600); }

  // ── Filters ──────────────────────────────────────────────────────────────
  onSearch(e: Event) { this.search.set((e.target as HTMLInputElement).value); }
  onType(e: Event) { this.typeFilter.set((e.target as HTMLSelectElement).value as '' | 'Scheduler' | 'Worker' | 'Maintenance'); }
  onStatus(e: Event) { this.statusFilter.set((e.target as HTMLSelectElement).value as '' | JobStatus); }
  onLastRun(e: Event) { this.lastRunFilter.set((e.target as HTMLSelectElement).value as '' | 'succeeded' | 'failed' | 'never'); }
  clearFilters() { this.search.set(''); this.typeFilter.set(''); this.statusFilter.set(''); this.lastRunFilter.set(''); }
  toggleSort(key: SortKey) {
    if (this.sortKey() === key) this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    else { this.sortKey.set(key); this.sortDir.set(key === 'name' ? 'asc' : 'desc'); }
  }

  // ── Display helpers ──────────────────────────────────────────────────────
  kindIcon(type: string): string { return KIND_ICON[type] ?? 'clock'; }
  statusTone(s: JobStatus): string { return s === 'failing' ? 'danger' : s === 'paused' ? 'amber' : 'green'; }
  statusLabel(s: JobStatus): string { return this.i18n.t(s === 'failing' ? 'bj_status_failing' : s === 'paused' ? 'bj_status_paused' : 'bj_status_healthy'); }
  queueDot(dot: string): string { return dot; }

  /** Minutes since an ISO timestamp → relative past ("3m" / "2h" / "never"). */
  agoIso(iso: string | null): string {
    if (!iso) return this.i18n.t('bj_never');
    const min = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 60000));
    if (min < 1) return this.i18n.t('bj_just_now');
    if (min < 60) return `${min}m`;
    if (min < 1440) return `${Math.round(min / 60)}h`;
    return `${Math.round(min / 1440)}d`;
  }
  /** ISO in the future → "in Nm" / "in Nh"; null → "—". */
  untilIso(iso: string | null): string {
    if (!iso) return '—';
    const min = Math.round((Date.parse(iso) - Date.now()) / 60000);
    if (min <= 0) return this.i18n.t('bj_due');
    if (min < 60) return this.i18n.t('bj_in', { t: `${min}m` });
    if (min < 1440) return this.i18n.t('bj_in', { t: `${Math.round(min / 60)}h` });
    return this.i18n.t('bj_in', { t: `${Math.round(min / 1440)}d` });
  }
  duration(ms: number | null | undefined): string {
    if (ms == null) return '—';
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  }
  /** KPI "next run" minutes → "in Nm". */
  nextRunKpi(min: number | null | undefined): string {
    if (min == null) return '—';
    if (min <= 0) return this.i18n.t('bj_due');
    if (min < 60) return this.i18n.t('bj_in', { t: `${min}m` });
    return this.i18n.t('bj_in', { t: `${Math.round(min / 60)}h` });
  }

  // ── Detail modal ──────────────────────────────────────────────────────────
  openDetail(j: Job) {
    this.modalTab.set('overview');
    this.error.set('');
    this.detailLoading.set(true);
    this.detail.set({ job: j, recentRuns: [] });
    this.detailOpen.set(true);
    this.service.getJob(j.id).subscribe({
      next: d => { this.detail.set(d); this.detailLoading.set(false); },
      error: () => { this.detailLoading.set(false); },
    });
  }
  openDrainJob(queue: JobQueue) {
    const j = this.jobs().find(x => x.id === queue.drainJobId);
    if (j) this.openDetail(j);
  }
  closeDetail() { this.detailOpen.set(false); this.detail.set(null); }
  setTab(tab: ModalTab) { this.modalTab.set(tab); }

  @HostListener('document:keydown.escape')
  onEscape() { if (this.detailOpen()) this.closeDetail(); }

  // ── Actions (INFRA_MANAGE) ──────────────────────────────────────────────
  private flashMsg(msg: string) {
    this.flash.set(msg);
    if (this.flashTimer) this.timers.clear(this.flashTimer);
    this.flashTimer = this.timers.set(() => this.flash.set(''), 3200);
  }
  private apply(updated: Job) {
    this.jobs.update(list => list.map(j => j.id === updated.id ? updated : j));
    if (this.detail()?.job.id === updated.id) this.detail.update(d => d ? { ...d, job: updated } : d);
  }

  async runNow(j: Job, event?: Event) {
    event?.stopPropagation();
    if (!this.canManage() || this.busy()) return;
    // The destructive retention sweep deletes data — confirm it.
    if (j.id === 'data-retention') {
      const ok = await this.confirmDialog.confirm({
        title: this.i18n.t('bj_run_title'),
        message: this.i18n.t('bj_run_retention_msg'),
        confirmText: this.i18n.t('bj_run_now'),
        cancelText: this.i18n.t('confirm_cancel'),
        tone: 'danger',
      });
      if (!ok) return;
    }
    this.busy.set(true);
    this.service.runNow(j.id).subscribe({
      next: u => { this.busy.set(false); this.apply(u); this.flashMsg(this.i18n.t('bj_flash_run', { name: j.name })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('bj_action_failed')); },
    });
  }

  async pause(j: Job, event?: Event) {
    event?.stopPropagation();
    if (!this.canManage()) return;
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('bj_pause_title'),
      message: this.i18n.t('bj_pause_msg', { name: j.name }),
      confirmText: this.i18n.t('bj_pause'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!ok) return;
    this.busy.set(true);
    this.service.pause(j.id).subscribe({
      next: u => { this.busy.set(false); this.apply(u); this.flashMsg(this.i18n.t('bj_flash_paused', { name: j.name })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('bj_action_failed')); },
    });
  }

  resume(j: Job, event?: Event) {
    event?.stopPropagation();
    if (!this.canManage() || this.busy()) return;
    this.busy.set(true);
    this.service.resume(j.id).subscribe({
      next: u => { this.busy.set(false); this.apply(u); this.flashMsg(this.i18n.t('bj_flash_resumed', { name: j.name })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('bj_action_failed')); },
    });
  }

  // ── Row menu ──────────────────────────────────────────────────────────────
  openMenuId = signal<string | null>(null);
  menuPos = signal<{ top: number; left: number } | null>(null);

  toggleMenu(id: string, event: Event) {
    event.stopPropagation();
    if (this.openMenuId() === id) { this.openMenuId.set(null); return; }
    const r = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.menuPos.set({ top: r.bottom + 4, left: Math.max(8, r.right - 184) });
    this.openMenuId.set(id);
  }

  @HostListener('document:click')
  onDocClick() { this.openMenuId.set(null); }

  @HostListener('window:scroll')
  @HostListener('window:resize')
  onViewportChange() { this.openMenuId.set(null); }
}
