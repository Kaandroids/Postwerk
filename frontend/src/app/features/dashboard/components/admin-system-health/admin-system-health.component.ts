import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminSystemHealthService } from '../../../../core/services/admin-system-health.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import {
  Subsystem,
  SubsystemStatus,
  SystemHealthKpis,
  SystemHealthEvent,
  MaintenanceMode,
} from '../../../../models/admin-system-health.model';

type ModalTab = 'overview' | 'checks';

/** Maps a subsystem kind to a decorative glyph (all icons exist in the shared icon set). */
const KIND_ICON: Record<string, string> = {
  API: 'bolt', Database: 'server', Cache: 'sliders', Scheduler: 'clock',
  Workers: 'refresh', Email: 'mail', External: 'sparkle',
};

/**
 * Platform-staff System Health: alert strip + KPI strip + subsystem status grid + recent-events
 * timeline + centered subsystem detail modal, with gated re-probe / cache-flush / maintenance toggle
 * (INFRA_MANAGE). Reads need INFRA_VIEW. Mutations are UX-gated only — the backend enforces them.
 */
@Component({
  selector: 'app-admin-system-health',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-system-health.component.html',
  styleUrl: './admin-system-health.component.scss',
})
export class AdminSystemHealthComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private service = inject(AdminSystemHealthService);
  private confirmDialog = inject(ConfirmDialogService);
  private timers = managedTimers();

  subsystems = signal<Subsystem[]>([]);
  kpis = signal<SystemHealthKpis | null>(null);
  events = signal<SystemHealthEvent[]>([]);
  maintenance = signal<MaintenanceMode | null>(null);

  loading = signal(true);
  error = signal('');
  refreshing = signal(false);
  updatedAt = signal('');
  alertDismissed = signal(false);

  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // Detail modal
  detailOpen = signal(false);
  detail = signal<Subsystem | null>(null);
  detailLoading = signal(false);
  modalTab = signal<ModalTab>('overview');
  busy = signal(false);

  /** The worst subsystem (down beats degraded) drives the alert strip. */
  alertSubsystem = computed(() =>
    this.subsystems().find(s => s.status === 'down') ?? this.subsystems().find(s => s.status === 'degraded') ?? null);

  readonly canManage = computed(() => this.identity.has('INFRA_MANAGE'));

  ngOnInit() {
    this.refreshAll();
    this.service.getMaintenance().subscribe({ next: m => this.maintenance.set(m), error: () => {} });
  }

  // ── Data loading ──────────────────────────────────────────────────────────
  loadSubsystems() {
    this.loading.set(true);
    this.error.set('');
    this.service.subsystems().subscribe({
      next: s => { this.subsystems.set(s); this.loading.set(false); },
      error: () => { this.subsystems.set([]); this.loading.set(false); this.error.set(this.i18n.t('sh_load_failed')); },
    });
  }

  loadKpis() {
    this.service.kpis().subscribe({ next: k => this.kpis.set(k), error: () => {} });
  }

  loadEvents() {
    this.service.events().subscribe({ next: e => this.events.set(e), error: () => this.events.set([]) });
  }

  private stampUpdated() {
    const d = new Date();
    const p = (n: number) => String(n).padStart(2, '0');
    this.updatedAt.set(`${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`);
  }

  private refreshAll() {
    this.loadKpis();
    this.loadSubsystems();
    this.loadEvents();
    this.stampUpdated();
  }

  refresh() {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    this.refreshAll();
    this.timers.set(() => this.refreshing.set(false), 600);
  }

  // ── Display helpers ──────────────────────────────────────────────────────
  kindIcon(kind: string): string {
    return KIND_ICON[kind] ?? 'server';
  }

  statusTone(status: SubsystemStatus): string {
    return status === 'down' ? 'danger' : status === 'degraded' ? 'amber' : 'green';
  }

  statusLabel(status: SubsystemStatus): string {
    return this.i18n.t(status === 'down' ? 'sh_status_down' : status === 'degraded' ? 'sh_status_degraded' : 'sh_status_ok');
  }

  ago(min: number | null | undefined): string {
    if (min == null) return '—';
    if (min < 1) return this.i18n.t('sh_just_now');
    if (min < 60) return `${Math.round(min)}m`;
    if (min < 1440) return `${Math.round(min / 60)}h`;
    return `${Math.round(min / 1440)}d`;
  }

  /** Humanizes uptime millis → "Nd Nh" / "Nh Nm" / "Nm". */
  uptime(ms: number | null | undefined): string {
    if (!ms) return '—';
    const s = Math.floor(ms / 1000);
    const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
    if (d > 0) return `${d}d ${h}h`;
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  }

  /** metrics object → ordered key/value rows for the detail modal. */
  metricRows(metrics: Record<string, unknown>): { key: string; value: unknown }[] {
    return Object.entries(metrics ?? {}).map(([key, value]) => ({ key, value }));
  }

  /** Subtitle counts: all-operational vs trouble breakdown. */
  readonly allOperational = computed(() => {
    const k = this.kpis();
    return !!k && k.down === 0 && k.degraded === 0;
  });

  // ── Detail modal ────────────────────────────────────────────────────────
  openDetail(s: Subsystem) {
    this.modalTab.set('overview');
    this.error.set('');
    this.detail.set(s);
    this.detailOpen.set(true);
    this.detailLoading.set(true);
    // Re-probe for the freshest snapshot.
    this.service.getSubsystem(s.id).subscribe({
      next: d => { this.detail.set(d); this.detailLoading.set(false); },
      error: () => { this.detailLoading.set(false); },
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
  }

  // ── Actions (INFRA_MANAGE) ──────────────────────────────────────────────
  private flashMsg(msg: string) {
    this.flash.set(msg);
    if (this.flashTimer) this.timers.clear(this.flashTimer);
    this.flashTimer = this.timers.set(() => this.flash.set(''), 3200);
  }

  private applySubsystem(d: Subsystem) {
    if (this.detail()?.id === d.id) this.detail.set(d);
    this.subsystems.update(list => list.map(s => s.id === d.id ? d : s));
  }

  probe(s: Subsystem) {
    if (!this.canManage() || this.busy()) return;
    this.busy.set(true);
    this.service.probe(s.id).subscribe({
      next: d => { this.busy.set(false); this.applySubsystem(d); this.flashMsg(this.i18n.t('sh_flash_probed', { name: d.name })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('sh_action_failed')); },
    });
  }

  async flushCache(s: Subsystem) {
    if (!this.canManage()) return;
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('sh_flush_title'),
      message: this.i18n.t('sh_flush_msg'),
      confirmText: this.i18n.t('sh_flush'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!ok) return;
    this.busy.set(true);
    this.service.flushCache().subscribe({
      next: () => { this.busy.set(false); this.flashMsg(this.i18n.t('sh_flash_flushed')); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('sh_action_failed')); },
    });
  }

  async toggleMaintenance() {
    if (!this.canManage()) return;
    const m = this.maintenance();
    const enabling = !(m?.enabled);
    const ok = await this.confirmDialog.confirm({
      title: enabling ? this.i18n.t('sh_maint_on_title') : this.i18n.t('sh_maint_off_title'),
      message: enabling ? this.i18n.t('sh_maint_on_msg') : this.i18n.t('sh_maint_off_msg'),
      confirmText: enabling ? this.i18n.t('sh_maint_enable') : this.i18n.t('sh_maint_disable'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: enabling ? 'danger' : 'accent',
    });
    if (!ok) return;
    this.busy.set(true);
    this.service.setMaintenance(enabling, null).subscribe({
      next: res => {
        this.busy.set(false);
        this.maintenance.set(res);
        this.flashMsg(this.i18n.t(enabling ? 'sh_flash_maint_on' : 'sh_flash_maint_off'));
      },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('sh_action_failed')); },
    });
  }
}
