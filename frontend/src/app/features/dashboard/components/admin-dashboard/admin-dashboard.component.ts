import { ChangeDetectionStrategy, Component, computed, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { ChartUtilService } from '../../../../core/services/chart-util.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { AdminSystemHealthService } from '../../../../core/services/admin-system-health.service';
import { AdminSubscriptionService } from '../../../../core/services/admin-subscription.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { AdminStats, AiUsageStats, TimelineDataPoint, AdminAuditLog } from '../../../../models/admin.model';
import { Subsystem, SubsystemStatus } from '../../../../models/admin-system-health.model';
import { SubscriptionKpis } from '../../../../models/admin-subscription.model';

/** Range options for the timeline period selector. Maps to backend timeline periods. */
type RangeKey = 'today' | '7d' | '30d' | '90d';

interface KpiCard {
  key: string;
  label: string;
  value: string;
  sub: string | null;
  /** Sparkline path data (line + area) or null when no honest series exists. */
  spark: { line: string; area: string } | null;
  iconName: string;
  tone: 'accent' | 'success' | 'warning' | 'danger' | 'violet';
}

/** A computed donut arc segment for the AI-spend-by-model chart. */
interface DonutArc {
  name: string;
  value: number;
  pct: number;
  dash: number;
  offset: number;
  tone: string;
}

/**
 * Platform Overview dashboard for staff. Renders real platform stats (KPIs, AI usage timeline,
 * AI spend by model donut) and honest empty states for surfaces that have no backend yet.
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss',
})
export class AdminDashboardComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private chart = inject(ChartUtilService);
  private adminService = inject(AdminService);
  private systemHealth = inject(AdminSystemHealthService);
  private subscriptions = inject(AdminSubscriptionService);
  private router = inject(Router);

  // ── State ──────────────────────────────────────────────────────────
  readonly stats = signal<AdminStats | null>(null);
  readonly aiUsage = signal<AiUsageStats | null>(null);
  readonly timeline = signal<TimelineDataPoint[]>([]);
  readonly subsystems = signal<Subsystem[]>([]);
  readonly subKpis = signal<SubscriptionKpis | null>(null);
  readonly recentActivity = signal<AdminAuditLog[]>([]);
  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly range = signal<RangeKey>('30d');
  readonly lastUpdated = signal<string>('');

  readonly ranges: RangeKey[] = ['today', '7d', '30d', '90d'];

  // Donut segment tones, cycled across models (semantic vars only).
  private readonly donutTones = ['accent', 'violet', 'success', 'warning', 'danger'];

  // ── Derived view-model ─────────────────────────────────────────────
  /** Sparkline line+area paths derived from the AI-usage timeline (the only honest series we have). */
  private readonly timelineSpark = computed(() => {
    const pts = this.timeline();
    if (pts.length < 2) return null;
    return {
      line: this.chart.svgLinePath(pts, 56, 28),
      area: this.chart.svgAreaPath(pts, 56, 28),
    };
  });

  /** KPI cards built strictly from real stats — no fabricated numbers. */
  readonly kpis = computed<KpiCard[]>(() => {
    const s = this.stats();
    if (!s) return [];
    const ai = this.aiUsage();
    const spark = this.timelineSpark();
    const aiSpend = ai ? ai.totalCostCents / 100 : 0;

    const cards: KpiCard[] = [
      {
        key: 'users',
        label: this.i18n.t('admin_kpi_total_users'),
        value: this.fmt.compactNumber(s.totalUsers),
        sub: s.newUsersLast7Days > 0
          ? `+${s.newUsersLast7Days} ${this.i18n.t('admin_stat_last_7_days')}`
          : null,
        spark: null,
        iconName: 'users',
        tone: 'accent',
      },
      {
        key: 'active-users',
        label: this.i18n.t('admin_stat_active_users'),
        value: this.fmt.compactNumber(s.activeUsers),
        sub: `${this.fmt.compactNumber(s.totalUsers)} ${this.i18n.t('admin_kpi_total_suffix')}`,
        spark: null,
        iconName: 'user',
        tone: 'violet',
      },
      {
        key: 'emails',
        label: this.i18n.t('admin_stat_emails'),
        value: this.fmt.compactNumber(s.totalEmails),
        sub: null,
        spark: null,
        iconName: 'mail',
        tone: 'success',
      },
      {
        key: 'aispend',
        label: this.i18n.t('admin_kpi_ai_spend'),
        value: this.formatEur(aiSpend),
        sub: this.i18n.t('admin_kpi_ai_spend_sub'),
        spark,
        iconName: 'sparkle',
        tone: 'warning',
      },
      {
        key: 'executions',
        label: this.i18n.t('admin_stat_executions'),
        value: this.fmt.compactNumber(s.totalAutomationExecutions),
        sub: `${this.successRate()}% ${this.i18n.t('admin_stat_success_rate')}`,
        spark: null,
        iconName: 'bolt',
        tone: 'accent',
      },
      {
        key: 'automations',
        label: this.i18n.t('admin_kpi_active_automations'),
        value: this.fmt.compactNumber(s.activeAutomations),
        sub: null,
        spark: null,
        iconName: 'automations',
        tone: 'violet',
      },
    ];

    // Derived billing KPI (real, from the subscriptions service) when available.
    const sub = this.subKpis();
    if (sub) {
      cards.push({
        key: 'mrr',
        label: this.i18n.t('psub_kpi_mrr'),
        value: this.formatEur(sub.mrr),
        sub: `${sub.activeSubscriptions} ${this.i18n.t('psub_active_subs')}`,
        spark: null,
        iconName: 'creditCard',
        tone: 'success',
      });
    }
    return cards;
  });

  // AI usage timeline chart paths.
  readonly chartLine = computed(() => this.chart.svgLinePath(this.timeline()));
  readonly chartArea = computed(() => this.chart.svgAreaPath(this.timeline()));
  readonly hasTimeline = computed(() => this.timeline().length > 1);

  /** Donut arcs for AI spend by model. Empty when there is no model breakdown. */
  readonly donutArcs = computed<DonutArc[]>(() => {
    const ai = this.aiUsage();
    if (!ai || ai.byModel.length === 0) return [];
    // Weight each model by its share of total tokens (we have no per-model cost from the API).
    const weighted = ai.byModel.map(m => ({ name: m.model, value: m.totalTokens }));
    const total = weighted.reduce((sum, m) => sum + m.value, 0) || 1;
    const circ = 2 * Math.PI * this.donutRadius;
    let offset = 0;
    return weighted.map((m, i) => {
      const frac = m.value / total;
      const dash = frac * circ;
      const arc: DonutArc = {
        name: m.name,
        value: m.value,
        pct: Math.round(frac * 100),
        dash,
        offset,
        tone: this.donutTones[i % this.donutTones.length],
      };
      offset += dash;
      return arc;
    });
  });

  readonly donutTotalLabel = computed(() => {
    const ai = this.aiUsage();
    return this.formatEur(ai ? ai.totalCostCents / 100 : 0);
  });

  // Donut geometry (matches the SVG viewBox in the template).
  readonly donutSize = 168;
  readonly donutRadius = 64;
  readonly donutStroke = 22;
  readonly donutCenter = 84;
  readonly donutCirc = 2 * Math.PI * 64;

  ngOnInit() {
    this.loadData();
  }

  private loadData() {
    let pending = 6;
    const done = () => { if (--pending <= 0) { this.loading.set(false); this.refreshing.set(false); this.stampUpdated(); } };

    this.adminService.getStats().subscribe({
      next: s => { this.stats.set(s); done(); },
      error: done,
    });
    this.adminService.getAiUsageStats().subscribe({
      next: a => { this.aiUsage.set(a); done(); },
      error: done,
    });
    this.adminService.getAiUsageTimeline(this.periodForRange(this.range())).subscribe({
      next: t => { this.timeline.set(t); done(); },
      error: done,
    });
    // Real cross-feature widgets (each gracefully degrades to empty on error / missing capability).
    this.systemHealth.subsystems().subscribe({ next: s => { this.subsystems.set(s); done(); }, error: done });
    this.subscriptions.kpis().subscribe({ next: k => { this.subKpis.set(k); done(); }, error: done });
    this.adminService.getAuditLog(undefined, undefined, 0, 8).subscribe({
      next: p => { this.recentActivity.set(p.content); done(); },
      error: done,
    });
  }

  /** Maps a UI range to the backend timeline period granularity. */
  private periodForRange(r: RangeKey): 'daily' | 'weekly' | 'monthly' {
    switch (r) {
      case 'today': return 'daily';
      case '7d': return 'daily';
      case '30d': return 'weekly';
      case '90d': return 'monthly';
    }
  }

  setRange(r: RangeKey) {
    if (this.range() === r) return;
    this.range.set(r);
    this.refreshing.set(true);
    this.adminService.getAiUsageTimeline(this.periodForRange(r)).subscribe({
      next: t => { this.timeline.set(t); this.refreshing.set(false); this.stampUpdated(); },
      error: () => { this.refreshing.set(false); },
    });
  }

  refresh() {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    this.loadData();
  }

  rangeLabel(r: RangeKey): string {
    return this.i18n.t('admin_range_' + r);
  }

  // ── System Health widget (real) ──────────────────────────────────────
  readonly healthSummary = computed(() => {
    const s = this.subsystems();
    return {
      down: s.filter(x => x.status === 'down').length,
      degraded: s.filter(x => x.status === 'degraded').length,
      ok: s.filter(x => x.status === 'ok').length,
      total: s.length,
    };
  });

  healthTone(status: SubsystemStatus): string {
    return status === 'down' ? 'danger' : status === 'degraded' ? 'amber' : 'green';
  }

  // ── Quick actions → real navigation, gated by capability ─────────────
  readonly quickActions: { key: string; labelKey: string; icon: string; path: string; perm: string }[] = [
    { key: 'grant-credit', labelKey: 'admin_qa_grant_credit', icon: 'sparkle', path: '/dashboard/admin/quota', perm: 'AI_USAGE_VIEW' },
    { key: 'create-plan', labelKey: 'admin_qa_create_plan', icon: 'creditCard', path: '/dashboard/admin/plans-subscriptions', perm: 'PLAN_VIEW' },
    { key: 'system-health', labelKey: 'nav_admin_system_health', icon: 'server', path: '/dashboard/admin/system-health', perm: 'INFRA_VIEW' },
  ];

  navTo(path: string) { this.router.navigate([path]); }

  successRate(): number {
    const s = this.stats();
    if (!s || s.totalAutomationExecutions === 0) return 0;
    return Math.round((s.successfulExecutions / s.totalAutomationExecutions) * 100);
  }

  /** strokeDasharray for a donut arc: "<dash> <gap>". */
  arcDashArray(a: DonutArc): string {
    return `${a.dash.toFixed(2)} ${(this.donutCirc - a.dash).toFixed(2)}`;
  }

  private stampUpdated() {
    const now = new Date();
    this.lastUpdated.set(
      now.toLocaleTimeString(this.i18n.lang() === 'de' ? 'de-DE' : 'en-US', {
        hour: '2-digit', minute: '2-digit', second: '2-digit',
      }),
    );
  }

  private formatEur(value: number): string {
    if (value >= 1000) return '€' + (value / 1000).toFixed(1) + 'k';
    return '€' + (Math.round(value * 100) / 100).toLocaleString(
      this.i18n.lang() === 'de' ? 'de-DE' : 'en-US',
      { minimumFractionDigits: 0, maximumFractionDigits: 2 },
    );
  }
}
