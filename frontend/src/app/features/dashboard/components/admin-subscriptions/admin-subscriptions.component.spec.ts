import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminSubscriptionsComponent } from './admin-subscriptions.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminSubscriptionService } from '../../../../core/services/admin-subscription.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminOrganizationService } from '../../../../core/services/admin-organization.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

/** Builds an `Event` whose target carries the given form value. */
const evt = (value: string) => ({ target: { value } }) as unknown as Event;

/** Minimal PlanModel-shaped fixture for the editor / catalog paths. */
const plan = (over: Record<string, unknown> = {}) => ({
  id: 'p1', name: 'Pro', price: 9, costLimitCents: 500, tokenLimit: 10000,
  automationLimit: 5, emailAccountLimit: 2, marketplacePublishEnabled: true,
  apiWebhookEnabled: false, inboundWebhookLimit: 0, ...over,
});

/**
 * Logic-only spec for the platform-staff Plans & Subscriptions console. The component renders via
 * TestBed (ngOnInit fired explicitly where a load is asserted) and its display helpers, computeds,
 * list-load, filter/sort/paginate, and permission-/confirm-gated mutations are exercised directly.
 * AdminSubscriptionService is the resource service; AdminIdentityService gates the actions.
 */
describe('AdminSubscriptionsComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let adminSvc: Record<string, ReturnType<typeof vi.fn>>;
  let orgSvc: Record<string, ReturnType<typeof vi.fn>>;
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminSubscriptionsComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      list: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
      kpis: vi.fn(() => of({ mrr: 0, activeSubscriptions: 0, aiCostCentsThisMonth: 0, overCapCount: 0, planCount: 0 })),
      get: vi.fn(() => of({ orgId: 'o1', orgName: 'Acme', planId: 'p1', planName: 'Pro' })),
      changePlan: vi.fn(() => of({ orgId: 'o1', planId: 'p2', planName: 'Max', effectiveCapCents: 0 })),
    };
    adminSvc = {
      getPlans: vi.fn(() => of([])),
      createPlan: vi.fn(() => of(plan())),
      updatePlan: vi.fn(() => of(plan())),
      deletePlan: vi.fn(() => of(undefined)),
    };
    orgSvc = {
      activate: vi.fn(() => of(undefined)),
      suspend: vi.fn(() => of(undefined)),
    };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminSubscriptionsComponent],
      providers: [
        provideRouter([]),
        { provide: I18nService, useValue: { t: (k: string) => k, lang: () => 'en' } },
        { provide: FormatService, useValue: {} },
        { provide: AdminSubscriptionService, useValue: svc },
        { provide: AdminService, useValue: adminSvc },
        { provide: AdminOrganizationService, useValue: orgSvc },
        { provide: AdminIdentityService, useValue: identity },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminSubscriptionsComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads rows + kpis + plans on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['list']).toHaveBeenCalled();
    expect(svc['kpis']).toHaveBeenCalled();
    expect(adminSvc['getPlans']).toHaveBeenCalled();
  });

  it('hasActiveFilters reflects any set filter', () => {
    expect(cmp.hasActiveFilters()).toBe(false);
    cmp.search.set('acme');
    expect(cmp.hasActiveFilters()).toBe(true);
  });

  it('sortedRows sorts by the active key + direction', () => {
    cmp.rows.set([
      { orgName: 'Beta', aiCostMicrosThisMonth: 0, effectiveCapCents: 0 },
      { orgName: 'Alpha', aiCostMicrosThisMonth: 0, effectiveCapCents: 0 },
    ] as never);
    expect(cmp.sortedRows().map(r => r.orgName)).toEqual(['Alpha', 'Beta']);
    cmp.sortDir.set('desc');
    expect(cmp.sortedRows().map(r => r.orgName)).toEqual(['Beta', 'Alpha']);
  });

  it('toggleSort flips on the same key, defaults direction per new key', () => {
    cmp.toggleSort('orgName'); // default key → flip to desc
    expect(cmp.sortDir()).toBe('desc');
    cmp.toggleSort('usage'); // new non-orgName key → desc
    expect(cmp.sortKey()).toBe('usage');
    expect(cmp.sortDir()).toBe('desc');
    cmp.toggleSort('orgName'); // back to orgName → asc
    expect(cmp.sortDir()).toBe('asc');
  });

  it('goToPage clamps to the valid range', () => {
    cmp.totalPages.set(3);
    cmp.goToPage(9);
    expect(cmp.currentPage()).toBe(0);
    cmp.goToPage(2);
    expect(cmp.currentPage()).toBe(2);
  });

  it('initial + avatarHue display helpers', () => {
    expect(cmp.initial('acme')).toBe('A');
    expect(cmp.initial('')).toBe('?');
    const h = cmp.avatarHue('acme');
    expect(h).toBeGreaterThanOrEqual(0);
    expect(h).toBeLessThan(360);
    expect(cmp.avatarHue('acme')).toBe(h);
  });

  it('money helpers: cents/micros/eur conversions', () => {
    expect(cmp.centsFromMicros(1_000_000)).toBe(100);
    expect(cmp.eurFromMicros(1_230_000)).toBe('€1.23');
    expect(cmp.eurFromCents(500)).toBe('€5.00');
    expect(typeof cmp.eur0(10)).toBe('string');
  });

  it('capLabel + limitLabel describe unlimited / off / capped', () => {
    expect(cmp.capLabel(-1)).toContain('psub_unlimited');
    expect(cmp.capLabel(0)).toBe('psub_ai_off');
    expect(cmp.capLabel(500)).toBe('€5.00');
    expect(cmp.limitLabel(-1)).toBe('∞');
    expect(cmp.limitLabel(5)).toBe('5');
  });

  it('usageRatio + usageTone derive from used/effective cap', () => {
    expect(cmp.usageRatio({ aiCostMicrosThisMonth: 0, effectiveCapCents: 0 })).toBe(0);
    // 1_000_000 micros → 100 cents; cap 200 → 0.5
    const s = { aiCostMicrosThisMonth: 1_000_000, effectiveCapCents: 200 };
    expect(cmp.usageRatio(s)).toBe(0.5);
    expect(cmp.usageTone(s)).toBe('green');
    expect(cmp.usageTone({ aiCostMicrosThisMonth: 0, effectiveCapCents: -1 })).toBe('unlim');
    expect(cmp.usageTone({ aiCostMicrosThisMonth: 1_900_000, effectiveCapCents: 200 })).toBe('danger');
  });

  it('loadRows stores the page payload', () => {
    svc['list'].mockReturnValue(of({ content: [{ orgId: 'o1' }], totalElements: 1, totalPages: 1 }));
    cmp.loadRows();
    expect(cmp.rows().length).toBe(1);
    expect(cmp.totalElements()).toBe(1);
    expect(cmp.loading()).toBe(false);
  });

  it('clearFilters / filterByPlan / inspectOverCap reset + reload', () => {
    cmp.search.set('x'); cmp.planFilter.set('Pro');
    cmp.clearFilters();
    expect(cmp.search()).toBe('');
    expect(cmp.planFilter()).toBe('');
    cmp.filterByPlan('Free');
    expect(cmp.planFilter()).toBe('Free');
    cmp.inspectOverCap();
    expect(cmp.usageFilter()).toBe('over90');
    expect(cmp.alertDismissed()).toBe(true);
  });

  it('openDetail fetches the subscription; closeDetail clears it', () => {
    cmp.openDetail({ orgId: 'o1' } as never);
    expect(svc['get']).toHaveBeenCalledWith('o1');
    expect(cmp.detail()?.orgId).toBe('o1');
    expect(cmp.detailOpen()).toBe(true);
    cmp.closeDetail();
    expect(cmp.detailOpen()).toBe(false);
    expect(cmp.detail()).toBeNull();
  });

  it('onChangePlanSelect captures the selection; setTab switches', () => {
    cmp.onChangePlanSelect(evt('p9'));
    expect(cmp.changePlanId()).toBe('p9');
    cmp.setTab('history');
    expect(cmp.modalTab()).toBe('history');
  });

  it('changePlan is gated on a real change + confirmation', async () => {
    cmp.detail.set({ orgId: 'o1', planId: 'p1', orgName: 'Acme' } as never);
    cmp.plans.set([plan({ id: 'p2', name: 'Max' })] as never);
    cmp.changePlanId.set('p1'); // same as current → no-op
    await cmp.changePlan();
    expect(svc['changePlan']).not.toHaveBeenCalled();
    cmp.changePlanId.set('p2');
    await cmp.changePlan();
    expect(svc['changePlan']).toHaveBeenCalledWith('o1', 'p2', null);
  });

  it('toggleSuspend suspends an active org after confirmation', async () => {
    cmp.detail.set({ orgId: 'o1', orgName: 'Acme', personal: false, status: 'active' } as never);
    await cmp.toggleSuspend();
    expect(orgSvc['suspend']).toHaveBeenCalledWith('o1', undefined);
  });

  it('toggleSuspend reactivates a suspended org without confirmation', async () => {
    cmp.detail.set({ orgId: 'o1', orgName: 'Acme', personal: false, status: 'suspended' } as never);
    await cmp.toggleSuspend();
    expect(orgSvc['activate']).toHaveBeenCalledWith('o1');
    expect(confirm.confirm).not.toHaveBeenCalled();
  });

  it('openGrantCredit requires QUOTA_OVERRIDE', () => {
    cmp.openGrantCredit();
    expect(cmp.grantCreditOpen()).toBe(true);
  });

  it('openNewPlan seeds the editor with defaults', () => {
    cmp.openNewPlan();
    expect(cmp.editorOpen()).toBe(true);
    expect(cmp.editorMode()).toBe('new');
    expect(cmp.eName()).toBe('');
    expect(cmp.editingPlanId()).toBeNull();
  });

  it('openEditPlan loads a plan into the editor', () => {
    cmp.openEditPlan(plan({ id: 'p7', name: 'Team', costLimitCents: -1 }) as never);
    expect(cmp.editorMode()).toBe('edit');
    expect(cmp.editingPlanId()).toBe('p7');
    expect(cmp.eName()).toBe('Team');
    expect(cmp.eCapMode()).toBe('unlimited');
  });

  it('editorNameMissing + editorCapMissing validation computeds', () => {
    expect(cmp.editorNameMissing()).toBe(true);
    cmp.eName.set('Pro');
    expect(cmp.editorNameMissing()).toBe(false);
    cmp.eCapMode.set('capped'); cmp.eCapAmount.set('');
    expect(cmp.editorCapMissing()).toBe(true);
    cmp.eCapAmount.set('5');
    expect(cmp.editorCapMissing()).toBe(false);
    cmp.eCapMode.set('off');
    expect(cmp.editorCapMissing()).toBe(false);
  });

  it('savePlan blocks on an invalid form, then creates / updates', () => {
    cmp.eName.set(''); // invalid
    cmp.savePlan();
    expect(adminSvc['createPlan']).not.toHaveBeenCalled();
    expect(cmp.eAttempted()).toBe(true);

    cmp.eName.set('Pro'); cmp.eCapMode.set('off');
    cmp.savePlan(); // create (no editingPlanId)
    expect(adminSvc['createPlan']).toHaveBeenCalled();

    cmp.editingPlanId.set('p1');
    cmp.savePlan(); // update
    expect(adminSvc['updatePlan']).toHaveBeenCalled();
  });

  it('deletePlan is confirm-gated', async () => {
    confirm.confirm.mockResolvedValue(false);
    await cmp.deletePlan(plan() as never);
    expect(adminSvc['deletePlan']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.deletePlan(plan() as never);
    expect(adminSvc['deletePlan']).toHaveBeenCalledWith('p1');
  });

  it('mutations are blocked without permission', () => {
    // identity.has() is a plain mock → a denied component must be built fresh with it false.
    TestBed.resetTestingModule();
    build({ has: () => false });
    cmp.openNewPlan();
    expect(cmp.editorOpen()).toBe(false);
    cmp.openGrantCredit();
    expect(cmp.grantCreditOpen()).toBe(false);
    cmp.eName.set('Pro'); cmp.eCapMode.set('off');
    cmp.savePlan();
    expect(adminSvc['createPlan']).not.toHaveBeenCalled();
  });
});
