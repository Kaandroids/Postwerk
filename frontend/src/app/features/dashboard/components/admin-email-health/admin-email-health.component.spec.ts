import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminEmailHealthComponent } from './admin-email-health.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { AdminEmailHealthService } from '../../../../core/services/admin-email-health.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

/**
 * Admin Email Health: logic-only spec following the admin-gdpr exemplar. The component is built via
 * TestBed (template auto-renders); ngOnInit is fired explicitly for the init-load assertion. Display
 * helpers, computeds (incl. the client-side sort), list/filter/sort/paginate flows and the gated
 * re-sync / pause / resume actions are exercised directly. Permission-denied paths use a fresh build.
 */
describe('AdminEmailHealthComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminEmailHealthComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      listMailboxes: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
      kpis: vi.fn(() => of({ total: 0, healthy: 0, failing: 0, authErrors: 0, paused: 0, avgSyncLagMinutes: null })),
      clusters: vi.fn(() => of([])),
      getMailbox: vi.fn(() => of({ id: 'm1', email: 'a@b.c', paused: false, recentAttempts: [] })),
      resync: vi.fn(() => of(undefined)),
      pause: vi.fn(() => of({ id: 'm1', email: 'a@b.c', paused: true })),
      resume: vi.fn(() => of({ id: 'm1', email: 'a@b.c', paused: false })),
    };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminEmailHealthComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: FormatService, useValue: {} },
        { provide: AdminIdentityService, useValue: identity },
        { provide: AdminEmailHealthService, useValue: svc },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminEmailHealthComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads kpis + clusters + rows on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['kpis']).toHaveBeenCalled();
    expect(svc['clusters']).toHaveBeenCalled();
    expect(svc['listMailboxes']).toHaveBeenCalled();
    expect(cmp.updatedAt()).not.toBe('');
  });

  it('display helpers: initial / ago / clusterDot', () => {
    expect(cmp.initial('bob')).toBe('B');
    expect(cmp.initial('')).toBe('?');

    expect(cmp.ago(null)).toBe('eh_never');
    expect(cmp.ago(0)).toBe('eh_just_now');
    expect(cmp.ago(30)).toBe('30m');
    expect(cmp.ago(120)).toBe('2h');
    expect(cmp.ago(2880)).toBe('2d');

    expect(cmp.clusterDot('down')).toBe('down');
    expect(cmp.clusterDot('warn')).toBe('warn');
    expect(cmp.clusterDot('ok')).toBe('ok');
    expect(cmp.clusterDot('anything')).toBe('ok');
  });

  it('healthTone / healthLabel (paused outranks the underlying health)', () => {
    expect(cmp.healthTone({ health: 'ok', paused: true })).toBe('amber');
    expect(cmp.healthTone({ health: 'failing', paused: false })).toBe('danger');
    expect(cmp.healthTone({ health: 'auth_error', paused: false })).toBe('amber');
    expect(cmp.healthTone({ health: 'ok', paused: false })).toBe('green');

    expect(cmp.healthLabel({ health: 'ok', paused: true })).toBe('eh_status_paused');
    expect(cmp.healthLabel({ health: 'failing', paused: false })).toBe('eh_status_failing');
    expect(cmp.healthLabel({ health: 'auth_error', paused: false })).toBe('eh_status_auth');
    expect(cmp.healthLabel({ health: 'ok', paused: false })).toBe('eh_status_ok');
  });

  it('avatarHue is a deterministic 0..359 hue', () => {
    const h = cmp.avatarHue('a@b.c');
    expect(h).toBeGreaterThanOrEqual(0);
    expect(h).toBeLessThan(360);
    expect(cmp.avatarHue('a@b.c')).toBe(h);
  });

  it('hasActiveFilters + downCluster computeds', () => {
    expect(cmp.hasActiveFilters()).toBe(false);
    cmp.search.set('x');
    expect(cmp.hasActiveFilters()).toBe(true);

    expect(cmp.downCluster()).toBeNull();
    cmp.clusters.set([
      { host: 'h1', status: 'ok' },
      { host: 'h2', status: 'down' },
    ] as never);
    expect(cmp.downCluster()?.host).toBe('h2');
  });

  it('sortedRows ranks failing first under the default health-desc sort', () => {
    cmp.rows.set([
      { email: 'b@x.com', health: 'ok', syncAgoMinutes: 5 },
      { email: 'a@x.com', health: 'failing', syncAgoMinutes: 10 },
      { email: 'c@x.com', health: 'auth_error', syncAgoMinutes: 1 },
    ] as never);
    expect(cmp.sortedRows().map(r => r.health)).toEqual(['failing', 'auth_error', 'ok']);
  });

  it('loadRows stores the page payload', () => {
    svc['listMailboxes'].mockReturnValue(of({ content: [{ id: 'm1' }], totalElements: 1, totalPages: 1 }));
    cmp.loadRows();
    expect(cmp.rows().length).toBe(1);
    expect(cmp.totalElements()).toBe(1);
    expect(cmp.totalPages()).toBe(1);
    expect(cmp.loading()).toBe(false);
  });

  it('toggleSort flips direction on the same key and resets on a new key', () => {
    cmp.toggleSort('health'); // same as default key → flip to asc
    expect(cmp.sortDir()).toBe('asc');
    cmp.toggleSort('email'); // new key → asc
    expect(cmp.sortKey()).toBe('email');
    expect(cmp.sortDir()).toBe('asc');
    cmp.toggleSort('sync'); // new key → desc
    expect(cmp.sortKey()).toBe('sync');
    expect(cmp.sortDir()).toBe('desc');
  });

  it('goToPage clamps to the valid range', () => {
    cmp.totalPages.set(3);
    cmp.goToPage(5);
    expect(cmp.currentPage()).toBe(0);
    cmp.goToPage(2);
    expect(cmp.currentPage()).toBe(2);
  });

  it('clearFilters resets every filter and reloads', () => {
    cmp.search.set('a'); cmp.protocolFilter.set('IMAP'); cmp.healthFilter.set('failing');
    cmp.serverFilter.set('h1'); cmp.syncFilter.set('stale');
    cmp.clearFilters();
    expect(cmp.search()).toBe('');
    expect(cmp.protocolFilter()).toBe('');
    expect(cmp.healthFilter()).toBe('');
    expect(cmp.serverFilter()).toBe('');
    expect(cmp.syncFilter()).toBe('');
    expect(svc['listMailboxes']).toHaveBeenCalled();
  });

  it('toggleCluster sets then unsets the server filter for the same host', () => {
    cmp.toggleCluster('h1');
    expect(cmp.serverFilter()).toBe('h1');
    cmp.toggleCluster('h1');
    expect(cmp.serverFilter()).toBe('');
  });

  it('inspectCluster pins the host and dismisses the alert', () => {
    cmp.inspectCluster('h2');
    expect(cmp.serverFilter()).toBe('h2');
    expect(cmp.alertDismissed()).toBe(true);
    expect(cmp.currentPage()).toBe(0);
  });

  it('openDetail seeds + loads the mailbox detail', () => {
    cmp.openDetail({ id: 'm1' } as never);
    expect(svc['getMailbox']).toHaveBeenCalledWith('m1');
    expect(cmp.detailOpen()).toBe(true);
    expect(cmp.detail()?.id).toBe('m1');
    expect(cmp.detailLoading()).toBe(false);
    expect(cmp.modalTab()).toBe('overview');
  });

  it('closeDetail / setTab manage the modal state', () => {
    cmp.detailOpen.set(true);
    cmp.setTab('attempts');
    expect(cmp.modalTab()).toBe('attempts');
    cmp.closeDetail();
    expect(cmp.detailOpen()).toBe(false);
    expect(cmp.detail()).toBeNull();
  });

  it('toggleMenu opens then closes for the same row id', () => {
    const evt = {
      stopPropagation: vi.fn(),
      currentTarget: { getBoundingClientRect: () => ({ bottom: 10, right: 200 }) },
    } as unknown as Event;
    cmp.toggleMenu('m1', evt);
    expect(cmp.openMenuId()).toBe('m1');
    expect(cmp.menuPos()).not.toBeNull();
    cmp.toggleMenu('m1', evt);
    expect(cmp.openMenuId()).toBeNull();
  });

  it('resync calls the service + flashes when allowed', () => {
    cmp.resync({ id: 'm1', email: 'a@b.c' });
    expect(svc['resync']).toHaveBeenCalledWith('m1');
    expect(cmp.flash()).toBe('eh_flash_resync');
    expect(cmp.busy()).toBe(false);
  });

  it('resync is blocked without INFRA_MANAGE', () => {
    TestBed.resetTestingModule();
    build({ has: () => false });
    cmp.resync({ id: 'm1', email: 'a@b.c' });
    expect(svc['resync']).not.toHaveBeenCalled();
  });

  it('pause requires confirmation and applies the mutation', async () => {
    cmp.rows.set([{ id: 'm1', email: 'a@b.c', paused: false }] as never);
    confirm.confirm.mockResolvedValue(false);
    await cmp.pause({ id: 'm1', email: 'a@b.c' });
    expect(svc['pause']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.pause({ id: 'm1', email: 'a@b.c' });
    expect(svc['pause']).toHaveBeenCalledWith('m1');
    expect(cmp.rows()[0].paused).toBe(true);
  });

  it('pause is blocked without INFRA_MANAGE', async () => {
    TestBed.resetTestingModule();
    build({ has: () => false });
    await cmp.pause({ id: 'm1', email: 'a@b.c' });
    expect(confirm.confirm).not.toHaveBeenCalled();
    expect(svc['pause']).not.toHaveBeenCalled();
  });

  it('resume calls the service and reflects the mutation', () => {
    cmp.rows.set([{ id: 'm1', email: 'a@b.c', paused: true }] as never);
    cmp.resume({ id: 'm1', email: 'a@b.c' });
    expect(svc['resume']).toHaveBeenCalledWith('m1');
    expect(cmp.rows()[0].paused).toBe(false);
  });
});
