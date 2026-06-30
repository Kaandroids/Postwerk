import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminUsersComponent } from './admin-users.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

/** Builds an `Event` whose target carries the given form value. */
const evt = (value: string) => ({ target: { value } }) as unknown as Event;

/**
 * Logic-only spec for the platform-staff Users console. The component renders via TestBed (template
 * auto-renders; ngOnInit is fired explicitly where data-load is asserted) and its display helpers,
 * computeds, list-load, and permission-/confirm-gated mutations are exercised directly. AdminService
 * is the resource service; AdminIdentityService gates the actions.
 */
describe('AdminUsersComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let identity: { has: ReturnType<typeof vi.fn>; identity: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminUsersComponent;

  function build(opts: { has?: () => boolean; email?: string } = {}) {
    svc = {
      getUsers: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
      getPlans: vi.fn(() => of([])),
      getUserOrganizations: vi.fn(() => of([])),
      getUserMailboxes: vi.fn(() => of([])),
      getAuditLog: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
      getUserSessions: vi.fn(() => of({ activeSessions: 3 })),
      getUserNotes: vi.fn(() => of([])),
      addUserNote: vi.fn(() => of({ id: 'n1', body: 'hi', authorEmail: 'me@x.com' })),
      deleteUserNote: vi.fn(() => of(undefined)),
      updateRole: vi.fn(() => of({ id: 'u1', role: 'USER' })),
      updateStaffRole: vi.fn(() => of({ id: 'u1', staffRole: 'ADMIN' })),
      assignPlan: vi.fn(() => of({ id: 'u1' })),
      disableUser: vi.fn(() => of(undefined)),
      resetUserPassword: vi.fn(() => of(undefined)),
      revokeUserSessions: vi.fn(() => of({ activeSessions: 0 })),
    };
    identity = {
      has: vi.fn(opts.has ?? (() => true)),
      identity: vi.fn(() => ({ email: opts.email ?? 'me@x.com' })),
    };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminUsersComponent],
      providers: [
        provideRouter([]),
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: FormatService, useValue: {} },
        { provide: AdminService, useValue: svc },
        { provide: AdminIdentityService, useValue: identity },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminUsersComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads users + plans on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['getUsers']).toHaveBeenCalled();
    expect(svc['getPlans']).toHaveBeenCalled();
  });

  it('sortedUsers sorts the current page by the active key + direction', () => {
    cmp.users.set([{ id: 'b', fullName: 'Bob' }, { id: 'a', fullName: 'Alice' }] as never);
    expect(cmp.sortedUsers().map(u => u.fullName)).toEqual(['Alice', 'Bob']);
    cmp.sortDir.set('desc');
    expect(cmp.sortedUsers().map(u => u.fullName)).toEqual(['Bob', 'Alice']);
  });

  it('planNames mirrors the loaded plans', () => {
    cmp.plans.set([{ name: 'Free' }, { name: 'Pro' }] as never);
    expect(cmp.planNames()).toEqual(['Free', 'Pro']);
  });

  it('rangeStart / rangeEnd describe the visible window', () => {
    expect(cmp.rangeStart()).toBe(0); // no rows
    cmp.totalElements.set(45);
    cmp.pageSize.set(20);
    cmp.currentPage.set(1);
    expect(cmp.rangeStart()).toBe(21);
    expect(cmp.rangeEnd()).toBe(40);
    cmp.currentPage.set(2);
    expect(cmp.rangeEnd()).toBe(45); // clamped to total
  });

  it('initials: two-part name, single name, and empty fallback', () => {
    expect(cmp.initials({ fullName: 'John Doe' } as never)).toBe('JD');
    expect(cmp.initials({ fullName: 'John' } as never)).toBe('JO');
    expect(cmp.initials({ fullName: '', email: '' } as never)).toBe('?');
  });

  it('avatarHue is a deterministic 0..359 hue', () => {
    const h = cmp.avatarHue({ id: 'u1' } as never);
    expect(h).toBeGreaterThanOrEqual(0);
    expect(h).toBeLessThan(360);
    expect(cmp.avatarHue({ id: 'u1' } as never)).toBe(h);
  });

  it('aiCapState reflects the cost limit', () => {
    expect(cmp.aiCapState({ costLimitCents: -1 } as never)).toBe('unlimited');
    expect(cmp.aiCapState({ costLimitCents: 0 } as never)).toBe('off');
    expect(cmp.aiCapState({ costLimitCents: 500 } as never)).toBe('capped');
  });

  it('aiUsageRatio + aiUsageTone derive from used/cap', () => {
    expect(cmp.aiUsageRatio({ costLimitCents: 0, aiCostMicrosThisMonth: 5 } as never)).toBe(0);
    // cap 100c → 1_000_000 micros; used 500_000 → 0.5
    const u = { costLimitCents: 100, aiCostMicrosThisMonth: 500_000 } as never;
    expect(cmp.aiUsageRatio(u)).toBe(0.5);
    expect(cmp.aiUsageTone(u)).toBe('green');
    expect(cmp.aiUsageTone({ costLimitCents: 100, aiCostMicrosThisMonth: 800_000 } as never)).toBe('amber');
    expect(cmp.aiUsageTone({ costLimitCents: 100, aiCostMicrosThisMonth: 1_000_000 } as never)).toBe('danger');
  });

  it('EUR formatting from micros and cents', () => {
    expect(cmp.eurFromMicros(1_230_000)).toBe('€1.23');
    expect(cmp.eurFromCents(500)).toBe('€5.00');
  });

  it('roleTone + statusTone badge mappings', () => {
    expect(cmp.roleTone('OWNER')).toBe('plum');
    expect(cmp.roleTone('ADMIN')).toBe('accent');
    expect(cmp.roleTone('member')).toBe('slate');
    expect(cmp.roleTone('')).toBe('slate');
    expect(cmp.statusTone('ACTIVE')).toBe('green');
    expect(cmp.statusTone('SUSPENDED')).toBe('slate');
  });

  it('loadUsers stores the page payload', () => {
    svc['getUsers'].mockReturnValue(of({ content: [{ id: 'u1' }], totalElements: 1, totalPages: 1 }));
    cmp.loadUsers();
    expect(cmp.users().length).toBe(1);
    expect(cmp.totalElements()).toBe(1);
    expect(cmp.loading()).toBe(false);
  });

  it('toggleSort flips on the same key, resets asc on a new key', () => {
    cmp.toggleSort('fullName'); // default key → flip to desc
    expect(cmp.sortDir()).toBe('desc');
    cmp.toggleSort('createdAt'); // new key → asc
    expect(cmp.sortKey()).toBe('createdAt');
    expect(cmp.sortDir()).toBe('asc');
  });

  it('goToPage clamps to the valid range', () => {
    cmp.totalPages.set(3);
    cmp.goToPage(5);
    expect(cmp.currentPage()).toBe(0);
    cmp.goToPage(2);
    expect(cmp.currentPage()).toBe(2);
  });

  it('selectUser opens the detail; closeDetail clears it', () => {
    cmp.selectUser({ id: 'u1' } as never);
    expect(cmp.selectedUser()?.id).toBe('u1');
    expect(cmp.activeTab()).toBe('overview');
    cmp.closeDetail();
    expect(cmp.selectedUser()).toBeNull();
  });

  it('tabPermission maps gated tabs; setTab honours the lock', () => {
    expect(cmp.tabPermission('activity')).toBe('AUDIT_LOG_VIEW');
    expect(cmp.tabPermission('staff')).toBe('STAFF_MANAGE');
    expect(cmp.tabPermission('overview')).toBeNull();
    expect(cmp.tabLocked('overview')).toBe(false);
    cmp.setTab('plan');
    expect(cmp.activeTab()).toBe('plan');
  });

  it('toggleRole flips ADMIN↔USER via the service', () => {
    cmp.users.set([{ id: 'u1', role: 'ADMIN' }] as never);
    cmp.toggleRole({ id: 'u1', role: 'ADMIN' } as never);
    expect(svc['updateRole']).toHaveBeenCalledWith('u1', 'USER');
  });

  it('onStaffRoleChange skips a no-op and calls through on a real change', () => {
    cmp.onStaffRoleChange({ id: 'u1', staffRole: null } as never, evt(''));
    expect(svc['updateStaffRole']).not.toHaveBeenCalled();
    cmp.onStaffRoleChange({ id: 'u1', staffRole: null } as never, evt('ADMIN'));
    expect(svc['updateStaffRole']).toHaveBeenCalledWith('u1', 'ADMIN');
  });

  it('onPlanChange ignores an empty selection', () => {
    cmp.onPlanChange({ id: 'u1' } as never, evt(''));
    expect(svc['assignPlan']).not.toHaveBeenCalled();
    cmp.onPlanChange({ id: 'u1' } as never, evt('p1'));
    expect(svc['assignPlan']).toHaveBeenCalledWith('u1', 'p1');
  });

  it('disableUser is confirm-gated', async () => {
    confirm.confirm.mockResolvedValue(false);
    await cmp.disableUser({ id: 'u1' } as never);
    expect(svc['disableUser']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.disableUser({ id: 'u1' } as never);
    expect(svc['disableUser']).toHaveBeenCalledWith('u1');
  });

  it('resetPassword needs permission + confirmation', async () => {
    confirm.confirm.mockResolvedValue(true);
    await cmp.resetPassword({ id: 'u1' } as never);
    expect(svc['resetUserPassword']).toHaveBeenCalledWith('u1');
  });

  it('resetPassword is blocked without USER_CREDENTIAL_RESET', async () => {
    // identity.has() is a plain mock → a denied component must be built fresh with it false.
    TestBed.resetTestingModule();
    build({ has: () => false });
    await cmp.resetPassword({ id: 'u1' } as never);
    expect(svc['resetUserPassword']).not.toHaveBeenCalled();
    expect(confirm.confirm).not.toHaveBeenCalled();
  });

  it('revokeSessions needs permission + confirmation', async () => {
    confirm.confirm.mockResolvedValue(true);
    await cmp.revokeSessions({ id: 'u1' } as never);
    expect(svc['revokeUserSessions']).toHaveBeenCalledWith('u1');
    expect(cmp.sessionsCount()).toBe(0);
  });

  it('addNote ignores an empty draft and posts a trimmed body', () => {
    cmp.noteDraft.set('   ');
    cmp.addNote({ id: 'u1' } as never);
    expect(svc['addUserNote']).not.toHaveBeenCalled();
    cmp.noteDraft.set('please review');
    cmp.addNote({ id: 'u1' } as never);
    expect(svc['addUserNote']).toHaveBeenCalledWith('u1', 'please review');
  });

  it('canDeleteNote allows the author', () => {
    expect(cmp.canDeleteNote({ authorEmail: 'me@x.com' } as never)).toBe(true);
  });

  it('deleteNote is gated by canDeleteNote + confirmation', async () => {
    confirm.confirm.mockResolvedValue(true);
    await cmp.deleteNote({ id: 'u1' } as never, { id: 'n1', authorEmail: 'me@x.com', body: 'x' } as never);
    expect(svc['deleteUserNote']).toHaveBeenCalledWith('u1', 'n1');
  });

  it('openGrantCredit requires the QUOTA_OVERRIDE permission', () => {
    cmp.openGrantCredit();
    expect(cmp.grantCreditOpen()).toBe(true);
  });

  it('openGrantCredit and canDeleteNote are denied without permission', () => {
    TestBed.resetTestingModule();
    build({ has: () => false, email: 'other@x.com' });
    cmp.openGrantCredit();
    expect(cmp.grantCreditOpen()).toBe(false);
    expect(cmp.canDeleteNote({ authorEmail: 'someone@x.com' } as never)).toBe(false);
    expect(cmp.tabLocked('activity')).toBe(true);
  });
});
