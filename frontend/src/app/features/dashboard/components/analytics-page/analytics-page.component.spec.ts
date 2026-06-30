import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { vi } from 'vitest';
import { AnalyticsPageComponent } from './analytics-page.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { OrganizationService } from '../../../../core/services/organization.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { PlanService } from '../../../../core/services/plan.service';
import { AnalyticsService } from '../../../../core/services/analytics.service';

/** Cast view onto the protected signals/computeds exercised here. */
interface Access {
  data: { set(v: unknown): void };
  sort: { set(v: string): void };
  range(): string;
  loading(): boolean;
  refreshing(): boolean;
  empty(): boolean;
  kpis(): unknown[];
  approvalStats(): unknown[];
  sortedAutomations(): { runs: number }[];
}

function fullData() {
  return {
    days: 30, activeAutomations: 4,
    kpis: {
      totalRuns: 100, successRate: 95, failedRuns: 5, emailsProcessed: 80, processedPct: 90,
      failRate: 5, pendingApprovals: 2, avgDecisionMinutes: 12, processedPct2: 0,
      deltas: { runs: 3 }, runsSeries: [1, 2], failsSeries: [0, 1], costSeries: [1],
    },
    aiCost: { byOperation: [], byModel: [], dailyCents: [] },
    topAutomations: [
      { runs: 1, successRate: 90, lastRunAt: null },
      { runs: 5, successRate: 50, lastRunAt: '2026-01-01T00:00:00Z' },
    ],
    approvals: { pending: 1, approved: 2, rejected: 0, expired: 0, avgDecisionMinutes: 5 },
  };
}

describe('AnalyticsPageComponent', () => {
  let plan: { costUnlimited: ReturnType<typeof vi.fn>; costUsagePercent: ReturnType<typeof vi.fn> };
  let analytics: { overview: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };
  let cmp: AnalyticsPageComponent;
  let acc: Access;

  beforeEach(() => {
    plan = { costUnlimited: vi.fn(() => false), costUsagePercent: vi.fn(() => null) };
    analytics = { overview: vi.fn(() => of(null)) };
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      imports: [AnalyticsPageComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k, lang: () => 'en' } },
        { provide: FormatService, useValue: { relativePast: () => 'past', relativeMinutes: () => 'min' } },
        { provide: OrganizationService, useValue: { activeOrg: () => ({ name: 'Org' }), activeOrgId: () => 'o1' } },
        { provide: WorkspaceService, useValue: { activeAccount: () => ({ email: 'a@b.c' }) } },
        { provide: PlanService, useValue: plan },
        { provide: AnalyticsService, useValue: analytics },
        { provide: Router, useValue: router },
      ],
    });
    cmp = TestBed.createComponent(AnalyticsPageComponent).componentInstance;
    acc = cmp as unknown as Access;
  });

  it('aiUsageValue renders ∞ / — / <1% / clamped percentage', () => {
    plan.costUnlimited.mockReturnValue(true);
    expect(cmp.aiUsageValue()).toBe('∞');
    plan.costUnlimited.mockReturnValue(false);
    plan.costUsagePercent.mockReturnValue(null);
    expect(cmp.aiUsageValue()).toBe('—');
    plan.costUsagePercent.mockReturnValue(0.5);
    expect(cmp.aiUsageValue()).toBe('<1%');
    plan.costUsagePercent.mockReturnValue(42);
    expect(cmp.aiUsageValue()).toBe('42%');
    plan.costUsagePercent.mockReturnValue(150);
    expect(cmp.aiUsageValue()).toBe('100%');
  });

  it('aiUsageColor escalates with usage', () => {
    plan.costUsagePercent.mockReturnValue(null);
    expect(cmp.aiUsageColor()).toBe('');
    plan.costUsagePercent.mockReturnValue(96);
    expect(cmp.aiUsageColor()).toBe('var(--danger)');
    plan.costUsagePercent.mockReturnValue(85);
    expect(cmp.aiUsageColor()).toBe('var(--warning)');
    plan.costUsagePercent.mockReturnValue(50);
    expect(cmp.aiUsageColor()).toBe('');
  });

  it('successTone / abs / nodeLabel helpers', () => {
    expect(cmp.successTone(50)).toBe('danger');
    expect(cmp.successTone(90)).toBe('success');
    expect(cmp.abs(-3)).toBe(3);
    // echo i18n → key === translation → falls back to the raw node type
    expect(cmp.nodeLabel('FILTER')).toBe('FILTER');
  });

  it('setRange changes only on a different value; setSort updates the sort', () => {
    cmp.setRange('30d'); // same as default
    expect(acc.range()).toBe('30d');
    cmp.setRange('7d');
    expect(acc.range()).toBe('7d');
    cmp.setSort('success');
    acc.data.set(fullData());
    // success ascending → the row with the lowest successRate (50%, runs=5) sorts first
    expect(acc.sortedAutomations()[0].runs).toBe(5);
  });

  it('empty is true only when loaded with zero total runs', () => {
    expect(acc.empty()).toBe(false); // no data
    acc.data.set({ kpis: { totalRuns: 0 } });
    expect(acc.empty()).toBe(true);
    acc.data.set({ kpis: { totalRuns: 5 } });
    expect(acc.empty()).toBe(false);
  });

  it('sortedAutomations orders by runs desc by default', () => {
    acc.data.set(fullData());
    expect(acc.sortedAutomations().map(r => r.runs)).toEqual([5, 1]);
  });

  it('load stores the overview and clears loading', () => {
    analytics.overview.mockReturnValue(of(fullData()));
    cmp.load();
    expect(analytics.overview).toHaveBeenCalled();
    expect(acc.loading()).toBe(false);
    expect(acc.kpis().length).toBe(6);
    expect(acc.approvalStats().length).toBe(4);
  });

  it('refresh sets the refreshing flag and reloads', () => {
    // A pending (non-emitting) request keeps the refreshing flag observable before it settles.
    analytics.overview.mockReturnValue(new Subject());
    cmp.refresh();
    expect(acc.refreshing()).toBe(true);
    expect(analytics.overview).toHaveBeenCalled();
  });

  it('subtitle returns a string', () => {
    expect(typeof cmp.subtitle()).toBe('string');
  });

  it('openDetail navigates to the per-automation analytics route', () => {
    cmp.openDetail('a1');
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard/analytics', 'a1']);
  });
});
