import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { ChartUtilService } from '../../../../core/services/chart-util.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { AdminSystemHealthService } from '../../../../core/services/admin-system-health.service';
import { AdminSubscriptionService } from '../../../../core/services/admin-subscription.service';

const STATS = {
  totalUsers: 1000,
  activeUsers: 500,
  deletedUsers: 0,
  newUsersLast7Days: 5,
  newUsersLast30Days: 20,
  totalPromptTokens: 0,
  totalOutputTokens: 0,
  totalAutomationExecutions: 100,
  successfulExecutions: 90,
  failedExecutions: 10,
  activeAutomations: 10,
  totalEmails: 200,
};

/**
 * Logic-only spec for the Platform Overview dashboard: the component renders via TestBed (its admin
 * services stubbed to observables) and the KPI/donut/health/timeline view-model computeds plus the
 * range/refresh/navigate action flows are exercised directly. Mirrors the admin-gdpr exemplar.
 */
describe('AdminDashboardComponent', () => {
  let admin: Record<string, ReturnType<typeof vi.fn>>;
  let health: { subsystems: ReturnType<typeof vi.fn> };
  let subs: { kpis: ReturnType<typeof vi.fn> };
  let chart: { svgLinePath: ReturnType<typeof vi.fn>; svgAreaPath: ReturnType<typeof vi.fn> };
  let cmp: AdminDashboardComponent;

  function build() {
    admin = {
      getStats: vi.fn(() => of(STATS)),
      getAiUsageStats: vi.fn(() => of({ totalPromptTokens: 0, totalOutputTokens: 0, totalTokens: 0, totalBillableChars: 0, totalCostCents: 0, byModel: [], byOperation: [] })),
      getAiUsageTimeline: vi.fn(() => of([])),
      getAuditLog: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
    };
    health = { subsystems: vi.fn(() => of([])) };
    subs = { kpis: vi.fn(() => of(null)) };
    chart = { svgLinePath: vi.fn(() => 'M0,0 L1,1'), svgAreaPath: vi.fn(() => 'M0,0 L1,1 Z') };
    TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: I18nService, useValue: { t: (k: string) => k, lang: () => 'en' } },
        { provide: FormatService, useValue: { compactNumber: (n: number) => String(n) } },
        { provide: ChartUtilService, useValue: chart },
        { provide: AdminService, useValue: admin },
        { provide: AdminIdentityService, useValue: { has: vi.fn(() => true) } },
        { provide: AdminSystemHealthService, useValue: health },
        { provide: AdminSubscriptionService, useValue: subs },
      ],
    });
    cmp = TestBed.createComponent(AdminDashboardComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loadData fans out to every widget source and clears loading', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(admin['getStats']).toHaveBeenCalled();
    expect(admin['getAiUsageStats']).toHaveBeenCalled();
    expect(admin['getAiUsageTimeline']).toHaveBeenCalled();
    expect(admin['getAuditLog']).toHaveBeenCalled();
    expect(health['subsystems']).toHaveBeenCalled();
    expect(subs['kpis']).toHaveBeenCalled();
    expect(cmp.loading()).toBe(false);
    expect(cmp.stats()).toEqual(STATS);
  });

  it('kpis computed is empty without stats and builds cards once loaded', () => {
    expect(cmp.kpis()).toEqual([]);
    cmp.stats.set(STATS as never);
    const cards = cmp.kpis();
    expect(cards.length).toBe(6); // no billing KPI yet
    expect(cards.map(c => c.key)).toContain('users');
    expect(cards.find(c => c.key === 'users')?.value).toBe('1000');
    expect(cards.find(c => c.key === 'users')?.sub).toBe('+5 admin_stat_last_7_days');
    // a billing KPI is appended when subscription KPIs exist
    cmp.subKpis.set({ mrr: 1500, activeSubscriptions: 3, aiCostCentsThisMonth: 0, overCapCount: 0, planCount: 2 } as never);
    expect(cmp.kpis().length).toBe(7);
    expect(cmp.kpis().find(c => c.key === 'mrr')?.value).toBe('€1.5k');
  });

  it('successRate is 0 without executions and a rounded percentage otherwise', () => {
    expect(cmp.successRate()).toBe(0);
    cmp.stats.set(STATS as never);
    expect(cmp.successRate()).toBe(90); // 90 / 100
    cmp.stats.set({ ...STATS, totalAutomationExecutions: 0 } as never);
    expect(cmp.successRate()).toBe(0);
  });

  it('donutArcs is empty without a model breakdown and computes weighted arcs otherwise', () => {
    expect(cmp.donutArcs()).toEqual([]);
    cmp.aiUsage.set({
      totalPromptTokens: 0, totalOutputTokens: 0, totalTokens: 400, totalBillableChars: 0, totalCostCents: 0, byOperation: [],
      byModel: [
        { model: 'gemini', promptTokens: 0, outputTokens: 0, totalTokens: 100 },
        { model: 'gpt', promptTokens: 0, outputTokens: 0, totalTokens: 300 },
      ],
    } as never);
    const arcs = cmp.donutArcs();
    expect(arcs.length).toBe(2);
    expect(arcs[0].pct).toBe(25);
    expect(arcs[1].pct).toBe(75);
    expect(arcs[1].offset).toBeCloseTo(arcs[0].dash);
  });

  it('donutTotalLabel formats the total AI spend in euros', () => {
    expect(cmp.donutTotalLabel()).toBe('€0');
    cmp.aiUsage.set({ totalPromptTokens: 0, totalOutputTokens: 0, totalTokens: 0, totalBillableChars: 0, totalCostCents: 250000, byModel: [], byOperation: [] } as never);
    expect(cmp.donutTotalLabel()).toBe('€2.5k'); // 250000 cents → €2500 → €2.5k
  });

  it('chart computeds delegate to ChartUtilService and hasTimeline guards short series', () => {
    expect(cmp.hasTimeline()).toBe(false);
    cmp.timeline.set([{ date: 'a', value: 1 }, { date: 'b', value: 2 }] as never);
    expect(cmp.hasTimeline()).toBe(true);
    expect(cmp.chartLine()).toBe('M0,0 L1,1');
    expect(cmp.chartArea()).toBe('M0,0 L1,1 Z');
    expect(chart.svgLinePath).toHaveBeenCalled();
    expect(chart.svgAreaPath).toHaveBeenCalled();
  });

  it('healthSummary counts subsystem statuses; healthTone maps to a tone', () => {
    cmp.subsystems.set([
      { status: 'ok' }, { status: 'ok' }, { status: 'degraded' }, { status: 'down' },
    ] as never);
    expect(cmp.healthSummary()).toEqual({ down: 1, degraded: 1, ok: 2, total: 4 });
    expect(cmp.healthTone('down')).toBe('danger');
    expect(cmp.healthTone('degraded')).toBe('amber');
    expect(cmp.healthTone('ok')).toBe('green');
  });

  it('setRange ignores the current range and reloads the timeline for a new one', () => {
    cmp.setRange('30d'); // already the default → no-op
    expect(admin['getAiUsageTimeline']).not.toHaveBeenCalled();
    cmp.setRange('7d');
    expect(cmp.range()).toBe('7d');
    expect(admin['getAiUsageTimeline']).toHaveBeenCalledWith('daily');
    expect(cmp.refreshing()).toBe(false);
  });

  it('refresh re-runs the full data load', () => {
    cmp.refresh();
    expect(admin['getStats']).toHaveBeenCalled();
    expect(cmp.refreshing()).toBe(false); // all stubs resolve synchronously
  });

  it('rangeLabel + arcDashArray are pure formatters', () => {
    expect(cmp.rangeLabel('90d')).toBe('admin_range_90d');
    expect(cmp.arcDashArray({ dash: 10 } as never)).toMatch(/^10\.00 /);
  });

  it('navTo routes to the given quick-action path', () => {
    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    cmp.navTo('/dashboard/admin/quota');
    expect(navSpy).toHaveBeenCalledWith(['/dashboard/admin/quota']);
  });
});
