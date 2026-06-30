import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AdminOrganizationsComponent } from './admin-organizations.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminOrganizationService } from '../../../../core/services/admin-organization.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

const emptyPage = { content: [], totalElements: 0, totalPages: 0 };

const orgDetail = (over: Record<string, unknown> = {}) => ({
  id: 'o1', name: 'Acme', slug: 'acme', personal: false, ownerUserId: 'owner', ownerEmail: null,
  ownerName: null, planName: 'PRO', memberCount: 2, createdAt: '', suspendedAt: null,
  mailboxCount: 0, automationCount: 0, aiCostMicrosThisMonth: 0, suspensionReason: null,
  members: [], ...over,
});

/**
 * Logic-only spec for the platform-staff Organizations screen. The component renders via TestBed
 * (its admin services stubbed to observables) and the avatar/badge helpers, computeds, list-load,
 * tab permissions, detail open/close and the suspend/transfer/delete flows are exercised directly.
 * Permission-gated paths that read a memoizing mock build a fresh component with the permission
 * denied from the start.
 */
describe('AdminOrganizationsComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let adminSvc: Record<string, ReturnType<typeof vi.fn>>;
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminOrganizationsComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      list: vi.fn(() => of(emptyPage)),
      get: vi.fn(() => of(orgDetail())),
      getAutomations: vi.fn(() => of([])),
      getMailboxes: vi.fn(() => of([])),
      suspend: vi.fn(() => of(orgDetail({ suspendedAt: '2026-01-01' }))),
      activate: vi.fn(() => of(orgDetail({ suspendedAt: null }))),
      transferOwnership: vi.fn(() => of(orgDetail())),
      remove: vi.fn(() => of(undefined)),
    };
    adminSvc = { getAuditLog: vi.fn(() => of(emptyPage)) };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminOrganizationsComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k, lang: () => 'en' } },
        { provide: FormatService, useValue: {} },
        { provide: AdminOrganizationService, useValue: svc },
        { provide: AdminService, useValue: adminSvc },
        { provide: AdminIdentityService, useValue: identity },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminOrganizationsComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads orgs on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['list']).toHaveBeenCalled();
  });

  it('avatar helpers: initial / avatarHue', () => {
    expect(cmp.initial('acme')).toBe('A');
    expect(cmp.initial('')).toBe('?');
    const h = cmp.avatarHue('acme');
    expect(h).toBeGreaterThanOrEqual(0);
    expect(h).toBeLessThan(360);
    expect(cmp.avatarHue('acme')).toBe(h); // deterministic
  });

  it('badge tones: automationStatusTone / kindTone', () => {
    expect(cmp.automationStatusTone('ACTIVE')).toBe('green');
    expect(cmp.automationStatusTone('testing')).toBe('amber');
    expect(cmp.automationStatusTone('PAUSED')).toBe('slate');
    expect(cmp.automationStatusTone('ERROR')).toBe('danger');
    expect(cmp.automationStatusTone('weird')).toBe('slate');
    expect(cmp.kindTone('INTEGRATION')).toBe('plum');
    expect(cmp.kindTone('AUTOMATION')).toBe('slate');
  });

  it('aiCostEur formats the monthly micros as EUR', () => {
    expect(cmp.aiCostEur()).toContain('0'); // no detail → 0
    cmp.detail.set(orgDetail({ aiCostMicrosThisMonth: 5_000_000 }) as never);
    expect(cmp.aiCostEur()).toContain('5');
  });

  it('transferCandidates excludes the current owner', () => {
    expect(cmp.transferCandidates()).toEqual([]);
    cmp.detail.set(orgDetail({
      ownerUserId: 'owner',
      members: [{ userId: 'owner' }, { userId: 'm2' }, { userId: 'm3' }],
    }) as never);
    const ids = cmp.transferCandidates().map(m => m.userId);
    expect(ids).toEqual(['m2', 'm3']);
  });

  it('loadOrgs stores the page payload', () => {
    svc['list'].mockReturnValue(of({ content: [{ id: 'o1' }], totalElements: 1, totalPages: 1 }));
    cmp.loadOrgs();
    expect(cmp.orgs().length).toBe(1);
    expect(cmp.totalElements()).toBe(1);
    expect(cmp.totalPages()).toBe(1);
    expect(cmp.loading()).toBe(false);
  });

  it('loadOrgs surfaces an error and clears the rows', () => {
    svc['list'].mockReturnValue(throwError(() => new Error('boom')));
    cmp.loadOrgs();
    expect(cmp.orgs()).toEqual([]);
    expect(cmp.loading()).toBe(false);
    expect(cmp.error()).toBe('admin_org_load_failed');
  });

  it('onTypeFilter resets the page and reloads', () => {
    svc['list'].mockClear();
    cmp.onTypeFilter({ target: { value: 'personal' } } as never);
    expect(cmp.typeFilter()).toBe('personal');
    expect(cmp.currentPage()).toBe(0);
    expect(svc['list']).toHaveBeenCalled();
  });

  it('goToPage sets the page and reloads', () => {
    svc['list'].mockClear();
    cmp.goToPage(3);
    expect(cmp.currentPage()).toBe(3);
    expect(svc['list']).toHaveBeenCalled();
  });

  it('tabPermission / tabLocked / setTab honour the staff role', () => {
    expect(cmp.tabPermission('billing')).toBe('BILLING_VIEW');
    expect(cmp.tabPermission('audit')).toBe('AUDIT_LOG_VIEW');
    expect(cmp.tabPermission('overview')).toBeNull();
    expect(cmp.tabLocked('billing')).toBe(false); // has() → true
    cmp.setTab('members');
    expect(cmp.activeTab()).toBe('members');
  });

  it('setTab is blocked for a locked tab', () => {
    TestBed.resetTestingModule();
    build({ has: () => false });
    expect(cmp.tabLocked('billing')).toBe(true);
    cmp.setTab('billing');
    expect(cmp.activeTab()).toBe('overview'); // unchanged
  });

  it('openDetail loads the detail and opens the modal', () => {
    cmp.openDetail({ id: 'o1', name: 'Acme' } as never);
    expect(svc['get']).toHaveBeenCalledWith('o1');
    expect(cmp.detailOpen()).toBe(true);
    expect(cmp.detail()?.id).toBe('o1');
    expect(cmp.activeTab()).toBe('overview');
    expect(cmp.detailLoading()).toBe(false);
  });

  it('closeDetail clears the modal state', () => {
    cmp.detail.set(orgDetail() as never);
    cmp.detailOpen.set(true);
    cmp.closeDetail();
    expect(cmp.detailOpen()).toBe(false);
    expect(cmp.detail()).toBeNull();
  });

  it('ensure* lazy loaders fetch their tab data', () => {
    const acc = cmp as unknown as {
      ensureAutomations(id: string): void;
      ensureMailboxes(id: string): void;
      ensureAudit(id: string): void;
    };
    acc.ensureAutomations('o1');
    expect(svc['getAutomations']).toHaveBeenCalledWith('o1');
    acc.ensureMailboxes('o1');
    expect(svc['getMailboxes']).toHaveBeenCalledWith('o1');
    acc.ensureAudit('o1');
    expect(adminSvc['getAuditLog']).toHaveBeenCalled();
  });

  it('toggleSuspend activates a currently-suspended org', async () => {
    cmp.detail.set(orgDetail({ suspendedAt: '2026-01-01' }) as never);
    await cmp.toggleSuspend();
    expect(svc['activate']).toHaveBeenCalledWith('o1');
    expect(svc['suspend']).not.toHaveBeenCalled();
  });

  it('toggleSuspend suspends after confirmation, passing the prompt reason', async () => {
    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue('spam');
    cmp.detail.set(orgDetail({ suspendedAt: null }) as never);
    await cmp.toggleSuspend();
    expect(svc['suspend']).toHaveBeenCalledWith('o1', 'spam');
    promptSpy.mockRestore();
  });

  it('toggleSuspend does nothing when the confirmation is dismissed', async () => {
    confirm.confirm.mockResolvedValue(false);
    cmp.detail.set(orgDetail({ suspendedAt: null }) as never);
    await cmp.toggleSuspend();
    expect(svc['suspend']).not.toHaveBeenCalled();
  });

  it('transferOwnership needs a non-personal org and a target', () => {
    cmp.detail.set(orgDetail({ personal: false }) as never);
    cmp.transferOwnership(); // no target yet
    expect(svc['transferOwnership']).not.toHaveBeenCalled();
    cmp.transferTarget.set('m2');
    cmp.transferOwnership();
    expect(svc['transferOwnership']).toHaveBeenCalledWith('o1', 'm2');
  });

  it('transferOwnership is blocked for a personal org', () => {
    cmp.detail.set(orgDetail({ personal: true }) as never);
    cmp.transferTarget.set('m2');
    cmp.transferOwnership();
    expect(svc['transferOwnership']).not.toHaveBeenCalled();
  });

  it('deleteOrg removes the org after confirmation', async () => {
    cmp.detail.set(orgDetail({ personal: false }) as never);
    await cmp.deleteOrg();
    expect(svc['remove']).toHaveBeenCalledWith('o1');
  });

  it('deleteOrg is blocked when the confirmation is dismissed', async () => {
    confirm.confirm.mockResolvedValue(false);
    cmp.detail.set(orgDetail({ personal: false }) as never);
    await cmp.deleteOrg();
    expect(svc['remove']).not.toHaveBeenCalled();
  });

  it('deleteOrg is blocked for a personal org', async () => {
    cmp.detail.set(orgDetail({ personal: true }) as never);
    await cmp.deleteOrg();
    expect(svc['remove']).not.toHaveBeenCalled();
  });

  it('openGrantCredit opens the dialog only with the QUOTA_OVERRIDE permission', () => {
    cmp.openGrantCredit();
    expect(cmp.grantCreditOpen()).toBe(true);
  });

  it('openGrantCredit is blocked without the permission', () => {
    TestBed.resetTestingModule();
    build({ has: () => false });
    cmp.openGrantCredit();
    expect(cmp.grantCreditOpen()).toBe(false);
  });

  it('onCreditGranted closes the dialog and flashes a message', () => {
    cmp.grantCreditOpen.set(true);
    cmp.onCreditGranted({ amountCents: 1000, targetName: 'Acme' });
    expect(cmp.grantCreditOpen()).toBe(false);
    expect(cmp.flash()).toBe('gcd_flash');
  });
});
