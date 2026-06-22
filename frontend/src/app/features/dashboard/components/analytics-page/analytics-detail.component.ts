import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AnalyticsService } from '../../../../core/services/analytics.service';
import { PageContentComponent } from '../page-content/page-content.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { humanizeError } from '../../../../shared/utils/error.util';
import { AnalyticsRange, AnalyticsTone, AutomationAnalyticsDetail } from '../../../../models/analytics.model';
import { SparklineComponent } from './charts/sparkline.component';
import { TrendChartComponent } from './charts/trend-chart.component';
import { BarComponent } from './charts/bar.component';

interface DetailTile {
  tone: AnalyticsTone;
  icon: string;
  label: string;
  value: string;
  sub: string;
  spark?: { data: number[]; tone: AnalyticsTone };
}

/** Automation detail (Screen 2): a single automation's trend, node failures, and recent runs. */
@Component({
  selector: 'app-analytics-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe, PageContentComponent, ErrorBannerComponent, EmptyStateComponent, IconComponent,
    SparklineComponent, TrendChartComponent, BarComponent,
  ],
  templateUrl: './analytics-detail.component.html',
  styleUrls: ['./analytics-page.component.scss'],
})
export class AnalyticsDetailComponent {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  private service = inject(AnalyticsService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  protected readonly ranges: AnalyticsRange[] = ['7d', '30d', '90d'];
  protected range = signal<AnalyticsRange>('30d');
  protected loading = signal(true);
  protected refreshing = signal(false);
  protected error = signal('');
  protected data = signal<AutomationAnalyticsDetail | null>(null);
  private id = signal<string>('');

  protected neverRun = computed(() => {
    const d = this.data();
    return !!d && d.kpis.runs === 0;
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((pm) => this.id.set(pm.get('id') ?? ''));
    effect(() => {
      this.id();
      this.range();
      untracked(() => { if (this.id()) this.load(); });
    });
  }

  private get locale(): string {
    return this.i18n.lang() === 'de' ? 'de-DE' : 'en-US';
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.service.automationDetail(this.id(), this.range()).subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); this.refreshing.set(false); },
      error: (e) => {
        this.error.set(humanizeError(e, this.i18n.t('an_error_title')));
        this.loading.set(false);
        this.refreshing.set(false);
      },
    });
  }

  setRange(r: AnalyticsRange): void { if (r !== this.range()) this.range.set(r); }
  refresh(): void { this.refreshing.set(true); this.load(); }
  back(): void { this.router.navigate(['/dashboard/analytics']); }

  // ── header helpers ─────────────────────────────────────────────────
  statusTone(status: string): AnalyticsTone {
    return status === 'ACTIVE' ? 'success' : status === 'PAUSED' ? 'warning' : 'violet';
  }
  statusLabel(status: string): string { return this.lookup('an_status_', status); }
  kindLabel(kind: string): string { return this.lookup('an_kind_', kind); }
  rel(iso: string | null): string { return this.fmt.relativePast(iso); }

  // ── KPI tiles ──────────────────────────────────────────────────────
  protected kpis = computed<DetailTile[]>(() => {
    const d = this.data();
    if (!d) return [];
    const k = d.kpis;
    return [
      { tone: 'accent', icon: 'zap', label: this.i18n.t('an_kpi_runs'), value: this.num(k.runs),
        spark: { data: k.runsSeries, tone: 'accent' }, sub: '' },
      { tone: 'success', icon: 'checkCircle', label: this.i18n.t('an_kpi_success'), value: k.successRate + '%',
        sub: this.i18n.t('an_kpi_sub_failed', { n: this.num(k.failedRuns) }) },
      { tone: 'danger', icon: 'x', label: this.i18n.t('an_kpi_failed'), value: this.num(k.failedRuns),
        sub: this.i18n.t('an_kpi_sub_failrate', { p: String(k.failRate) }),
        spark: { data: k.failsSeries, tone: 'danger' } },
      { tone: 'violet', icon: 'mail', label: this.i18n.t('an_kpi_emails'), value: this.num(k.emailsProcessed),
        sub: this.i18n.t('an_kpi_sub_processed', { p: String(k.processedPct) }) },
      { tone: 'warning', icon: 'spark', label: this.i18n.t('an_kpi_ai_share'),
        value: k.aiSharePct != null ? k.aiSharePct + '%' : '—',
        sub: this.i18n.t('an_kpi_ai_share_sub') },
    ];
  });

  // ── recent runs ────────────────────────────────────────────────────
  runTone(status: string): AnalyticsTone {
    return status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'danger' : 'accent';
  }
  runStatus(status: string): string { return this.lookup('an_run_', status); }
  duration(ms: number | null): string {
    if (ms == null) return this.i18n.t('an_running');
    if (ms < 1000) return ms + 'ms';
    return (ms / 1000).toFixed(1) + 's';
  }

  nodeLabel(nodeType: string): string {
    const key = 'an_node_' + nodeType.toLowerCase();
    const v = this.i18n.t(key);
    return v === key ? nodeType : v;
  }

  private lookup(prefix: string, value: string): string {
    const key = prefix + (value || '').toLowerCase();
    const v = this.i18n.t(key);
    return v === key ? value : v;
  }

  private num(n: number): string { return (n ?? 0).toLocaleString(this.locale); }
}
