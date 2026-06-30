import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminQuotaComponent } from './admin-quota.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminQuotaService } from '../../../../core/services/admin-quota.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminOrganizationService } from '../../../../core/services/admin-organization.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

const emptyPage = { content: [], totalElements: 0, totalPages: 0 };

/**
 * Logic-only spec for the platform-staff Quota Overrides screen. The component renders via TestBed
 * (its admin services stubbed to observables) and the money/row helpers, modal computeds, validation,
 * list-load and create/update/revoke flows are exercised directly. Permission-gated paths that read a
 * memoizing mock build a fresh component with the permission denied from the start.
 */
describe('AdminQuotaComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let adminSvc: Record<string, ReturnType<typeof vi.fn>>;
  let orgSvc: Record<string, ReturnType<typeof vi.fn>>;
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminQuotaComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      list: vi.fn(() => of(emptyPage)),
      kpis: vi.fn(() => of({ activeCount: 0, creditGrantedThisMonthCents: 0, over80Count: 0, expiringIn7Count: 0 })),
      create: vi.fn(() => of({ id: 'q1' })),
      update: vi.fn(() => of({ id: 'q1' })),
      revoke: vi.fn(() => of(undefined)),
    };
    adminSvc = { getUsers: vi.fn(() => of(emptyPage)) };
    orgSvc = { list: vi.fn(() => of(emptyPage)) };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminQuotaComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k, lang: () => 'en' } },
        { provide: FormatService, useValue: {} },
        { provide: AdminQuotaService, useValue: svc },
        { provide: AdminService, useValue: adminSvc },
        { provide: AdminOrganizationService, useValue: orgSvc },
        { provide: AdminIdentityService, useValue: identity },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminQuotaComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads kpis + rows on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['kpis']).toHaveBeenCalled();
    expect(svc['list']).toHaveBeenCalled();
  });

  it('euro formats cents and renders the unlimited glyph for null', () => {
    expect(cmp.euro(null)).toBe('∞');
    expect(cmp.euro(undefined)).toBe('∞');
    const formatted = cmp.euro(123456);
    expect(formatted).not.toBe('∞');
    expect(formatted).toContain('1');
  });

  it('row helpers: initial / avatarHue', () => {
    expect(cmp.initial('alice')).toBe('A');
    expect(cmp.initial('')).toBe('?');
    const h = cmp.avatarHue('acme');
    expect(h).toBeGreaterThanOrEqual(0);
    expect(h).toBeLessThan(360);
    expect(cmp.avatarHue('acme')).toBe(h); // deterministic
  });

  it('mini-bar helpers: capFillPct / capTone / capRaised', () => {
    expect(cmp.capFillPct({ effectiveCapCents: null } as never)).toBe(100);
    expect(cmp.capFillPct({ effectiveCapCents: 0 } as never)).toBe(0);
    expect(cmp.capFillPct({ effectiveCapCents: 1000, currentSpendCents: 500 } as never)).toBe(50);
    expect(cmp.capFillPct({ effectiveCapCents: 1000, currentSpendCents: 5000 } as never)).toBe(100); // clamped

    expect(cmp.capTone({ effectiveCapCents: null } as never)).toBe('unlim');
    expect(cmp.capTone({ effectiveCapCents: 0 } as never)).toBe('green');
    expect(cmp.capTone({ effectiveCapCents: 1000, currentSpendCents: 500 } as never)).toBe('green');
    expect(cmp.capTone({ effectiveCapCents: 1000, currentSpendCents: 850 } as never)).toBe('amber');
    expect(cmp.capTone({ effectiveCapCents: 1000, currentSpendCents: 950 } as never)).toBe('danger');

    expect(cmp.capRaised({ effectiveCapCents: 2000, baseCapCents: 1000 } as never)).toBe(true);
    expect(cmp.capRaised({ effectiveCapCents: 500, baseCapCents: 1000 } as never)).toBe(false);
  });

  it('baseCapCents derives from plan default or an explicit seeded cap', () => {
    expect(cmp.baseCapCents()).toBe(0); // no target
    cmp.target.set({ type: 'USER', id: 'u1', name: 'A', slug: 'a@b.c', plan: 'PRO' } as never);
    expect(cmp.baseCapCents()).toBe(20000); // PLAN_BASE_CAP['PRO']
    cmp.target.set({ type: 'USER', id: 'u1', name: 'A', slug: 'a@b.c', plan: 'PRO', baseCapCents: 5000 } as never);
    expect(cmp.baseCapCents()).toBe(5000); // explicit wins
  });

  it('amountCents / previewEffectiveCents / needsAmount track kind + amount', () => {
    cmp.target.set({ type: 'USER', id: 'u1', name: 'A', slug: 'a@b.c', plan: 'PRO' } as never);
    cmp.amount.set('12.5');
    expect(cmp.amountCents()).toBe(1250);

    cmp.formKind.set('UNLIMITED');
    expect(cmp.needsAmount()).toBe(false);
    expect(cmp.previewEffectiveCents()).toBeNull();

    cmp.formKind.set('CREDIT');
    expect(cmp.needsAmount()).toBe(true);
    expect(cmp.previewEffectiveCents()).toBe(20000 + 1250); // base + amount

    cmp.formKind.set('CAP');
    expect(cmp.previewEffectiveCents()).toBe(1250); // amount only
  });

  it('validation computeds: targetMissing / amountMissing / reasonMissing', () => {
    expect(cmp.targetMissing()).toBe(true);
    cmp.target.set({ type: 'USER', id: 'u1', name: 'A', slug: 'a@b.c', plan: 'PRO' } as never);
    expect(cmp.targetMissing()).toBe(false);

    cmp.formKind.set('CREDIT');
    cmp.amount.set('');
    expect(cmp.amountMissing()).toBe(true);
    cmp.amount.set('10');
    expect(cmp.amountMissing()).toBe(false);

    expect(cmp.reasonMissing()).toBe(true);
    cmp.reason.set('budget bump');
    expect(cmp.reasonMissing()).toBe(false);
  });

  it('isView / targetLocked / hasActiveFilters reflect mode + filters', () => {
    expect(cmp.isView()).toBe(false);
    expect(cmp.targetLocked()).toBe(false); // default mode = create
    cmp.modalMode.set('view');
    expect(cmp.isView()).toBe(true);
    expect(cmp.targetLocked()).toBe(true);

    expect(cmp.hasActiveFilters()).toBe(false);
    cmp.search.set('acme');
    expect(cmp.hasActiveFilters()).toBe(true);
  });

  it('loadRows stores the page payload', () => {
    svc['list'].mockReturnValue(of({ content: [{ id: 'q1' }], totalElements: 1, totalPages: 1 }));
    cmp.loadRows();
    expect(cmp.rows().length).toBe(1);
    expect(cmp.totalElements()).toBe(1);
    expect(cmp.totalPages()).toBe(1);
    expect(cmp.loading()).toBe(false);
  });

  it('clearFilters resets every filter and reloads', () => {
    cmp.search.set('x');
    cmp.targetTypeFilter.set('USER');
    cmp.kindFilter.set('CREDIT');
    cmp.statusFilter.set('active');
    cmp.expiryFilter.set('next7');
    svc['list'].mockClear();
    cmp.clearFilters();
    expect(cmp.search()).toBe('');
    expect(cmp.hasActiveFilters()).toBe(false);
    expect(cmp.currentPage()).toBe(0);
    expect(svc['list']).toHaveBeenCalled();
  });

  it('goToPage sets the page and reloads', () => {
    svc['list'].mockClear();
    cmp.goToPage(2);
    expect(cmp.currentPage()).toBe(2);
    expect(svc['list']).toHaveBeenCalled();
  });

  it('openCreate opens the modal in create mode when allowed', () => {
    cmp.openCreate();
    expect(cmp.modalOpen()).toBe(true);
    expect(cmp.modalMode()).toBe('create');
    expect(cmp.editing()).toBeNull();
  });

  it('openCreate is blocked without the QUOTA_OVERRIDE permission', () => {
    // identity.has is a plain mock read directly here, so deny it from a fresh build.
    TestBed.resetTestingModule();
    build({ has: () => false });
    cmp.openCreate();
    expect(cmp.modalOpen()).toBe(false);
  });

  it('openView seeds the form from the row in view mode', () => {
    cmp.openView({
      id: 'q1', targetType: 'USER', targetId: 'u1', targetName: 'Alice', targetEmailOrSlug: 'a@b.c',
      basePlan: 'PRO', kind: 'CREDIT', amountCents: 1000, baseCapCents: 20000, effectiveCapCents: 21000,
      currentSpendCents: 0, expiresAt: null, reason: 'r', grantedByName: 'staff', createdAt: '', status: 'active',
    } as never);
    expect(cmp.modalMode()).toBe('view');
    expect(cmp.modalOpen()).toBe(true);
    expect(cmp.target()?.id).toBe('u1');
    expect(cmp.formKind()).toBe('CREDIT');
    expect(cmp.amount()).toBe('10'); // 1000 cents → €10
  });

  it('enterEdit switches a view modal into edit mode (gated)', () => {
    cmp.editing.set({ id: 'q1', kind: 'CREDIT' } as never);
    cmp.modalMode.set('view');
    cmp.enterEdit();
    expect(cmp.modalMode()).toBe('edit');
  });

  it('setKind updates the kind but is a no-op in view mode', () => {
    cmp.setKind('CAP');
    expect(cmp.formKind()).toBe('CAP');
    cmp.modalMode.set('view');
    cmp.setKind('CREDIT');
    expect(cmp.formKind()).toBe('CAP'); // unchanged in view
  });

  it('save is blocked while the form is invalid', () => {
    cmp.save();
    expect(cmp.attempted()).toBe(true);
    expect(svc['create']).not.toHaveBeenCalled();
  });

  it('save creates a new override from a valid form', () => {
    cmp.target.set({ type: 'USER', id: 'u1', name: 'Alice', slug: 'a@b.c', plan: 'PRO' } as never);
    cmp.formKind.set('CREDIT');
    cmp.amount.set('10');
    cmp.reason.set('budget bump');
    cmp.save();
    expect(svc['create']).toHaveBeenCalled();
    expect(svc['update']).not.toHaveBeenCalled();
  });

  it('save updates the override when editing an existing row', () => {
    cmp.target.set({ type: 'USER', id: 'u1', name: 'Alice', slug: 'a@b.c', plan: 'PRO' } as never);
    cmp.formKind.set('CREDIT');
    cmp.amount.set('10');
    cmp.reason.set('budget bump');
    cmp.editing.set({ id: 'q9' } as never);
    cmp.save();
    expect(svc['update']).toHaveBeenCalledWith('q9', expect.anything());
  });

  it('revoke removes the row after confirmation', async () => {
    cmp.rows.set([{ id: 'q1', kind: 'CREDIT', targetName: 'Alice', basePlan: 'PRO' } as never]);
    await cmp.revoke({ id: 'q1', kind: 'CREDIT', targetName: 'Alice', basePlan: 'PRO' } as never);
    expect(svc['revoke']).toHaveBeenCalledWith('q1');
    expect(cmp.rows().length).toBe(0);
  });

  it('revoke does nothing when the confirmation is dismissed', async () => {
    confirm.confirm.mockResolvedValue(false);
    await cmp.revoke({ id: 'q1', kind: 'CREDIT', targetName: 'Alice', basePlan: 'PRO' } as never);
    expect(svc['revoke']).not.toHaveBeenCalled();
  });

  it('closeModal clears modal + picker state', () => {
    cmp.modalOpen.set(true);
    cmp.pickerOpen.set(true);
    cmp.modalError.set('boom');
    cmp.closeModal();
    expect(cmp.modalOpen()).toBe(false);
    expect(cmp.pickerOpen()).toBe(false);
    expect(cmp.modalError()).toBe('');
  });
});
