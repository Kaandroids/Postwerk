import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminStaffComponent } from './admin-staff.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminStaffService } from '../../../../core/services/admin-staff.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

const DAY = 86_400_000;
const daysAgoIso = (n: number) => new Date(Date.now() - n * DAY).toISOString();

/**
 * Logic-only spec for the platform-staff Staff & Roles console: the component renders via TestBed
 * (its AdminStaffService stubbed to observables) and the display helpers / computeds / filter+paging
 * / grant-revoke-setRole action flows are exercised directly. Mirrors the admin-gdpr exemplar.
 */
describe('AdminStaffComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminStaffComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      roster: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
      kpis: vi.fn(() => of({ total: 0, superAdmins: 0, privileged: 0, readOnly: 0, added30d: 0 })),
      roles: vi.fn(() => of({ roles: [], allPermissions: [] })),
      setRole: vi.fn(() => of({ id: 'm1', name: 'Bob', email: 'bob@x.com', role: 'ADMIN', tier: 'PRIVILEGED', capabilityCount: 0, lastActiveAt: null, staffSince: null, self: false })),
      revoke: vi.fn(() => of(undefined)),
      candidates: vi.fn(() => of([])),
    };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminStaffComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: FormatService, useValue: {} },
        { provide: AdminStaffService, useValue: svc },
        { provide: AdminIdentityService, useValue: identity },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminStaffComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads kpis + matrix + roster on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['kpis']).toHaveBeenCalled();
    expect(svc['roles']).toHaveBeenCalled();
    expect(svc['roster']).toHaveBeenCalled();
    expect(cmp.updatedAt()).not.toBe('');
  });

  it('display helpers: initial / roleLabel / roleIcon / tierLabel / passthroughs', () => {
    expect(cmp.initial('alice')).toBe('A');
    expect(cmp.initial('')).toBe('?');
    expect(cmp.roleLabel(null)).toBe('—');
    expect(cmp.roleLabel('SUPER_ADMIN')).toBe('sf_role_super_admin');
    expect(cmp.roleIcon('SUPER_ADMIN')).toBe('key');
    expect(cmp.roleIcon('ADMIN')).toBe('shield');
    expect(cmp.roleIcon('BILLING')).toBe('card');
    expect(cmp.roleIcon('MODERATOR')).toBe('market');
    expect(cmp.roleIcon('SUPPORT')).toBe('help');
    expect(cmp.roleIcon('AUDITOR')).toBe('search');
    expect(cmp.rolePurpose('ADMIN')).toBe('sf_purpose_admin');
    expect(cmp.tierLabel(null)).toBe('—');
    expect(cmp.tierLabel('PRIVILEGED')).toBe('sf_tier_privileged');
    expect(cmp.tierLabel('READ_ONLY')).toBe('sf_tier_readonly');
    expect(cmp.capLabel('sf_cap_user_view')).toBe('sf_cap_user_view');
    expect(cmp.areaLabel('sf_area_users')).toBe('sf_area_users');
  });

  it('avatarHue is a deterministic 0..359 hue', () => {
    const h = cmp.avatarHue('bob@x.com');
    expect(h).toBeGreaterThanOrEqual(0);
    expect(h).toBeLessThan(360);
    expect(cmp.avatarHue('bob@x.com')).toBe(h);
  });

  it('capsFor / capCount / topAreas read the role matrix', () => {
    cmp.matrix.set({
      roles: [{ key: 'ADMIN', tier: 'PRIVILEGED', privileged: true, permissions: ['USER_VIEW', 'USER_MANAGE', 'ORG_VIEW'] }],
      allPermissions: [],
    } as never);
    expect(cmp.capsFor('ADMIN')).toEqual(['USER_VIEW', 'USER_MANAGE', 'ORG_VIEW']);
    expect(cmp.capCount('ADMIN')).toBe(3);
    expect(cmp.capCount('SUPPORT')).toBe(0); // missing role → []
    // topAreas maps non-platform areas that contain a granted cap
    expect(cmp.topAreas('ADMIN')).toBe('sf_area_users · sf_area_orgs');
  });

  it('relative renders human-friendly elapsed buckets', () => {
    expect(cmp.relative(null)).toBe('—');
    expect(cmp.relative(daysAgoIso(0))).toBe('sf_active_today');
    expect(cmp.relative(daysAgoIso(1))).toBe('sf_ago_yesterday');
    expect(cmp.relative(daysAgoIso(5))).toBe('sf_ago_days');
    expect(cmp.relative(daysAgoIso(90))).toBe('sf_ago_months');
    expect(cmp.relative(daysAgoIso(800))).toBe('sf_ago_years');
  });

  it('pickedChanged / granted reflect the detail selection', () => {
    cmp.matrix.set({
      roles: [{ key: 'ADMIN', tier: 'PRIVILEGED', privileged: true, permissions: ['USER_MANAGE'] }],
      allPermissions: [],
    } as never);
    cmp.editing.set({ id: 'm1', role: 'SUPPORT' } as never);
    cmp.picked.set('SUPPORT');
    expect(cmp.pickedChanged()).toBe(false);
    cmp.picked.set('ADMIN');
    expect(cmp.pickedChanged()).toBe(true);
    expect(cmp.granted('USER_MANAGE')).toBe(true);
    expect(cmp.granted('BILLING_VIEW')).toBe(false);
  });

  it('canManage / hasFilters / soleSuperAdmin computeds', () => {
    expect(cmp.canManage()).toBe(true);
    expect(cmp.hasFilters()).toBe(false);
    cmp.roleF.set('ADMIN');
    expect(cmp.hasFilters()).toBe(true);
    cmp.kpis.set({ total: 1, superAdmins: 1, privileged: 1, readOnly: 0, added30d: 0 });
    expect(cmp.soleSuperAdmin()).toBe(true);
    cmp.alertDismissed.set(true);
    expect(cmp.soleSuperAdmin()).toBe(false);
  });

  it('loadRoster stores the page payload', () => {
    svc['roster'].mockReturnValue(of({ content: [{ id: 'm1' }], totalElements: 1, totalPages: 1 }));
    cmp.loadRoster();
    expect(cmp.rows().length).toBe(1);
    expect(cmp.total()).toBe(1);
    expect(cmp.totalPages()).toBe(1);
    expect(cmp.loading()).toBe(false);
  });

  it('filter setters reset the page and reload', () => {
    cmp.page.set(3);
    cmp.onRole({ target: { value: 'ADMIN' } } as unknown as Event);
    expect(cmp.roleF()).toBe('ADMIN');
    expect(cmp.page()).toBe(0);
    expect(svc['roster']).toHaveBeenCalled();

    cmp.onTier({ target: { value: 'PRIVILEGED' } } as unknown as Event);
    expect(cmp.tierF()).toBe('PRIVILEGED');

    cmp.toggleSort('lastActive');
    expect(cmp.sortKey()).toBe('lastActive');

    cmp.clearFilters();
    expect(cmp.roleF()).toBe('');
    expect(cmp.tierF()).toBe('');
    expect(cmp.search()).toBe('');
  });

  it('inspectBanner focuses the SUPER_ADMIN roster slice', () => {
    cmp.inspectBanner();
    expect(cmp.view()).toBe('staff');
    expect(cmp.roleF()).toBe('SUPER_ADMIN');
    expect(svc['roster']).toHaveBeenCalled();
  });

  it('goToPage clamps to the valid range', () => {
    cmp.totalPages.set(3);
    cmp.goToPage(5);
    expect(cmp.page()).toBe(0);
    cmp.goToPage(2);
    expect(cmp.page()).toBe(2);
  });

  it('openMember seeds the detail modal; closeDetail clears it', () => {
    cmp.openMember({ id: 'm1', role: 'BILLING' } as never);
    expect(cmp.editing()?.id).toBe('m1');
    expect(cmp.picked()).toBe('BILLING');
    expect(cmp.detailTab()).toBe('caps');
    cmp.closeDetail();
    expect(cmp.editing()).toBeNull();
  });

  it('saveRole is blocked for self and when nothing changed', () => {
    cmp.editing.set({ id: 'm1', role: 'SUPPORT', self: true } as never);
    cmp.picked.set('ADMIN');
    cmp.saveRole();
    expect(svc['setRole']).not.toHaveBeenCalled();

    cmp.editing.set({ id: 'm1', role: 'SUPPORT', self: false } as never);
    cmp.picked.set('SUPPORT'); // unchanged
    cmp.saveRole();
    expect(svc['setRole']).not.toHaveBeenCalled();
  });

  it('saveRole persists the picked role and updates the row', () => {
    cmp.rows.set([{ id: 'm1', name: 'Bob', role: 'SUPPORT', self: false }] as never);
    cmp.editing.set({ id: 'm1', name: 'Bob', role: 'SUPPORT', self: false } as never);
    cmp.picked.set('ADMIN');
    cmp.saveRole();
    expect(svc['setRole']).toHaveBeenCalledWith('m1', 'ADMIN');
    expect(cmp.rows()[0].role).toBe('ADMIN');
    expect(cmp.busy()).toBe(false);
  });

  it('revokeMember requires confirmation then deletes', async () => {
    cmp.rows.set([{ id: 'm1', name: 'Bob', self: false }] as never);
    cmp.editing.set({ id: 'm1', name: 'Bob', self: false } as never);
    confirm.confirm.mockResolvedValue(false);
    await cmp.revokeMember();
    expect(svc['revoke']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.revokeMember();
    expect(svc['revoke']).toHaveBeenCalledWith('m1');
    expect(cmp.rows().length).toBe(0);
  });

  it('rowRevoke deletes the row after confirmation', async () => {
    cmp.rows.set([{ id: 'm1', name: 'Bob', self: false }] as never);
    await cmp.rowRevoke({ id: 'm1', name: 'Bob', self: false } as never, { stopPropagation() {} } as Event);
    expect(svc['revoke']).toHaveBeenCalledWith('m1');
    expect(cmp.rows().length).toBe(0);
  });

  it('grant flow: openGrant / selectCandidate / submitGrant', () => {
    cmp.openGrant();
    expect(cmp.granting()).toBe(true);
    cmp.selectCandidate({ id: 'c1', name: 'Carol', email: 'carol@x.com' } as never);
    expect(cmp.grantSelected()?.id).toBe('c1');
    expect(cmp.grantResults().length).toBe(0);
    cmp.setGrantRole('MODERATOR');
    cmp.submitGrant();
    expect(svc['setRole']).toHaveBeenCalledWith('c1', 'MODERATOR');
  });

  it('toggleMenu opens then closes the row menu', () => {
    const evt = { stopPropagation() {}, currentTarget: { getBoundingClientRect: () => ({ bottom: 10, right: 200 }) } } as unknown as Event;
    cmp.toggleMenu('m1', evt);
    expect(cmp.openMenuId()).toBe('m1');
    expect(cmp.menuPos()).not.toBeNull();
    cmp.toggleMenu('m1', evt);
    expect(cmp.openMenuId()).toBeNull();
  });

  it('grant actions are blocked without STAFF_MANAGE permission', () => {
    // canManage() memoizes a plain mock fn, so the denied case needs a fresh build.
    TestBed.resetTestingModule();
    build({ has: () => false });
    cmp.openGrant();
    expect(cmp.granting()).toBe(false);
    cmp.grantSelected.set({ id: 'c1', name: 'Carol', email: 'carol@x.com' } as never);
    cmp.submitGrant();
    expect(svc['setRole']).not.toHaveBeenCalled();
  });
});
