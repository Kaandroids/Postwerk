import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { OrganizationService } from '../../../../core/services/organization.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { PlanService } from '../../../../core/services/plan.service';
import { AnalyticsService } from '../../../../core/services/analytics.service';
import { PageContentComponent } from '../page-content/page-content.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { humanizeError } from '../../../../shared/utils/error.util';
import {
  AnalyticsOverview, AnalyticsRange, AnalyticsTone, COST_TONES, TopAutomation,
} from '../../../../models/analytics.model';
import { SparklineComponent } from './charts/sparkline.component';
import { TrendChartComponent } from './charts/trend-chart.component';
import { DonutComponent, DonutSegment } from './charts/donut.component';
import { MiniCostLineComponent } from './charts/mini-cost-line.component';
import { BarComponent } from './charts/bar.component';

interface KpiTile {
  tone: AnalyticsTone;
  icon: string;
  label: string;
  value: string;
  valueColor?: string;
  sub: string;
  delta?: number | null;
  spark?: { data: number[]; tone: AnalyticsTone };
  testid: string;
}

type SortKey = 'runs' | 'success' | 'lastrun';

/** Analytics overview (Screen 1): org-wide automation performance + AI cost over 7d/30d/90d. */
@Component({
  selector: 'app-analytics-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe, PageContentComponent, ErrorBannerComponent, EmptyStateComponent, IconComponent,
    SparklineComponent, TrendChartComponent, DonutComponent, MiniCostLineComponent, BarComponent,
  ],
  templateUrl: './analytics-page.component.html',
  styleUrl: './analytics-page.component.scss',
})
export class AnalyticsPageComponent {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  private organizations = inject(OrganizationService);
  private workspace = inject(WorkspaceService);
  private planService = inject(PlanService);
  private service = inject(AnalyticsService);
  private router = inject(Router);

  protected readonly ranges: AnalyticsRange[] = ['7d', '30d', '90d'];
  protected range = signal<AnalyticsRange>('30d');
  protected loading = signal(true);
  protected refreshing = signal(false);
  protected error = signal('');
  protected data = signal<AnalyticsOverview | null>(null);

  protected costDim = signal<'operation' | 'model'>('operation');
  protected sort = signal<SortKey>('runs');

  protected orgName = computed(() => this.organizations.activeOrg()?.name ?? '');
  protected mailbox = computed(() => this.workspace.activeAccount()?.email ?? '');

  protected empty = computed(() => {
    const d = this.data();
    return !!d && d.kpis.totalRuns === 0;
  });

  constructor() {
    // Reload whenever the active org or the range changes (org scope is applied server-side).
    effect(() => {
      this.organizations.activeOrgId();
      this.range();
      untracked(() => this.load());
    });
  }

  private get locale(): string {
    return this.i18n.lang() === 'de' ? 'de-DE' : 'en-US';
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.service.overview(this.range()).subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); this.refreshing.set(false); },
      error: (e) => {
        this.error.set(humanizeError(e, this.i18n.t('an_error_title')));
        this.loading.set(false);
        this.refreshing.set(false);
      },
    });
  }

  setRange(r: AnalyticsRange): void {
    if (r !== this.range()) this.range.set(r);
  }

  refresh(): void {
    this.refreshing.set(true);
    this.load();
  }

  subtitle(): string {
    const d = this.data();
    return this.i18n.t('an_subtitle', { days: String(d?.days ?? ''), n: String(d?.activeAutomations ?? 0) });
  }

  // ── KPI tiles ──────────────────────────────────────────────────────
  protected kpis = computed<KpiTile[]>(() => {
    const d = this.data();
    if (!d) return [];
    const k = d.kpis;
    return [
      {
        tone: 'accent', icon: 'zap', label: this.i18n.t('an_kpi_runs'),
        value: this.num(k.totalRuns), delta: k.deltas.runs, sub: this.i18n.t('an_kpi_sub_vs_prev'),
        spark: { data: k.runsSeries, tone: 'accent' }, testid: 'analytics-kpi-runs',
      },
      {
        tone: 'success', icon: 'checkCircle', label: this.i18n.t('an_kpi_success'),
        value: k.successRate + '%', sub: this.i18n.t('an_kpi_sub_failed', { n: this.num(k.failedRuns) }),
        testid: 'analytics-kpi-success',
      },
      {
        tone: 'violet', icon: 'mail', label: this.i18n.t('an_kpi_emails'),
        value: this.num(k.emailsProcessed), sub: this.i18n.t('an_kpi_sub_processed', { p: String(k.processedPct) }),
        testid: 'analytics-kpi-emails',
      },
      {
        tone: 'danger', icon: 'x', label: this.i18n.t('an_kpi_failed'),
        value: this.num(k.failedRuns), sub: this.i18n.t('an_kpi_sub_failrate', { p: String(k.failRate) }),
        spark: { data: k.failsSeries, tone: 'danger' }, testid: 'analytics-kpi-failed',
      },
      {
        tone: 'warning', icon: 'spark', label: this.i18n.t('an_kpi_ai_usage'),
        value: this.aiUsageValue(), valueColor: this.aiUsageColor(),
        sub: this.i18n.t('an_kpi_this_month'),
        spark: { data: k.costSeries, tone: 'warning' }, testid: 'analytics-kpi-cost',
      },
      {
        tone: 'accent', icon: 'clock', label: this.i18n.t('an_kpi_pending'),
        value: this.num(k.pendingApprovals),
        sub: this.i18n.t('an_kpi_sub_decide', { h: this.avgLabel(k.avgDecisionMinutes) }),
        testid: 'analytics-kpi-pending',
      },
    ];
  });

  // ── cost donut segments ────────────────────────────────────────────
  protected costSegments = computed<DonutSegment[]>(() => {
    const c = this.data()?.aiCost;
    if (!c) return [];
    const slices = this.costDim() === 'operation' ? c.byOperation : c.byModel;
    return slices.map((s, i) => ({
      label: this.costDim() === 'operation' ? this.opLabel(s.key) : s.key,
      costCents: s.costCents,
      tone: COST_TONES[i % COST_TONES.length] as AnalyticsTone,
    }));
  });

  // AI cost is never shown as money — the donut center mirrors the monthly usage % headline.
  protected costCenter = computed(() => this.aiUsageValue());
  protected dailyCostSeries = computed(() => (this.data()?.aiCost.dailyCents ?? []).map((d) => d.cents));

  /** Monthly AI usage as a display string (shared with the topbar limiter): %, ∞, <1%, or —. */
  aiUsageValue(): string {
    if (this.planService.costUnlimited()) return '∞';
    const p = this.planService.costUsagePercent();
    if (p == null) return '—';
    if (p > 0 && p < 1) return '<1%';
    return Math.min(Math.round(p), 100) + '%';
  }

  aiUsageColor(): string {
    const p = this.planService.costUsagePercent();
    if (p == null) return '';
    const r = Math.min(Math.round(p), 100);
    if (r >= 95) return 'var(--danger)';
    if (r >= 80) return 'var(--warning)';
    return '';
  }

  // ── top automations (sorted) ───────────────────────────────────────
  protected sortedAutomations = computed<TopAutomation[]>(() => {
    const rows = [...(this.data()?.topAutomations ?? [])];
    const s = this.sort();
    if (s === 'runs') rows.sort((a, b) => b.runs - a.runs);
    else if (s === 'success') rows.sort((a, b) => a.successRate - b.successRate);
    else rows.sort((a, b) => this.ts(b.lastRunAt) - this.ts(a.lastRunAt));
    return rows.slice(0, 10);
  });

  setSort(s: SortKey): void { this.sort.set(s); }

  // ── approvals ──────────────────────────────────────────────────────
  protected approvalStats = computed(() => {
    const a = this.data()?.approvals;
    if (!a) return [];
    return [
      { key: 'an_appr_pending', value: a.pending, tone: 'warning' as AnalyticsTone },
      { key: 'an_appr_approved', value: a.approved, tone: 'success' as AnalyticsTone },
      { key: 'an_appr_rejected', value: a.rejected, tone: 'danger' as AnalyticsTone },
      { key: 'an_appr_expired', value: a.expired, tone: 'violet' as AnalyticsTone },
    ];
  });

  avgDecision(): string { return this.avgLabel(this.data()?.approvals.avgDecisionMinutes ?? null); }
  abs(n: number): number { return Math.abs(n); }

  openDetail(id: string): void {
    this.router.navigate(['/dashboard/analytics', id]);
  }

  goToAutomations(): void { this.router.navigate(['/dashboard/automations']); }
  goToApprovals(): void { this.router.navigate(['/dashboard/approvals']); }

  // ── label helpers ──────────────────────────────────────────────────
  nodeLabel(nodeType: string): string {
    const key = 'an_node_' + nodeType.toLowerCase();
    const v = this.i18n.t(key);
    return v === key ? nodeType : v;
  }

  successTone(rate: number): AnalyticsTone { return rate < 80 ? 'danger' : 'success'; }
  rel(iso: string | null): string { return this.fmt.relativePast(iso); }

  private opLabel(op: string): string {
    const key = 'an_op_' + op.toLowerCase();
    const v = this.i18n.t(key);
    return v === key ? op : v;
  }

  private num(n: number): string { return (n ?? 0).toLocaleString(this.locale); }

  private avgLabel(min: number | null): string {
    if (min == null) return '—';
    return this.fmt.relativeMinutes(min);
  }

  private ts(iso: string | null): number { return iso ? new Date(iso).getTime() : 0; }
}
