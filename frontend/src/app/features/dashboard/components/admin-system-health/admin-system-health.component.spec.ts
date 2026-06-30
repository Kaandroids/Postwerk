import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminSystemHealthComponent } from './admin-system-health.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { AdminSystemHealthService } from '../../../../core/services/admin-system-health.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

const sub = (over: Partial<Record<string, unknown>> = {}) => ({
  id: 's1', name: 'API', kind: 'API', version: '1.0', status: 'ok', primary: 'fast',
  metrics: {}, lastCheckedMinutes: 1, lastError: null, recentChecks: [], ...over,
}) as never;

/**
 * Admin System Health spec — mirrors the admin-gdpr exemplar: TestBed renders the template,
 * ngOnInit is fired manually, and the display helpers / computeds / load + detail + action flows
 * are exercised directly with the admin service stubbed.
 */
describe('AdminSystemHealthComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminSystemHealthComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      subsystems: vi.fn(() => of([])),
      kpis: vi.fn(() => of({ down: 0, degraded: 0, ok: 3, total: 3 })),
      events: vi.fn(() => of([])),
      getSubsystem: vi.fn(() => of(sub())),
      probe: vi.fn(() => of(sub({ status: 'ok' }))),
      flushCache: vi.fn(() => of(undefined)),
      getMaintenance: vi.fn(() => of({ enabled: false, message: null, updatedAt: null })),
      setMaintenance: vi.fn(() => of({ enabled: true, message: null, updatedAt: null })),
    };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminSystemHealthComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: FormatService, useValue: {} },
        { provide: AdminIdentityService, useValue: identity },
        { provide: AdminSystemHealthService, useValue: svc },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminSystemHealthComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads kpis + subsystems + events + maintenance on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['kpis']).toHaveBeenCalled();
    expect(svc['subsystems']).toHaveBeenCalled();
    expect(svc['events']).toHaveBeenCalled();
    expect(svc['getMaintenance']).toHaveBeenCalled();
    expect(cmp.maintenance()?.enabled).toBe(false);
    expect(cmp.updatedAt()).not.toBe('');
  });

  it('loadSubsystems stores rows and clears loading', () => {
    svc['subsystems'].mockReturnValue(of([sub(), sub({ id: 's2' })]));
    cmp.loadSubsystems();
    expect(cmp.subsystems().length).toBe(2);
    expect(cmp.loading()).toBe(false);
  });

  it('loadKpis / loadEvents store their payloads', () => {
    svc['kpis'].mockReturnValue(of({ down: 1, degraded: 0 }) as never);
    cmp.loadKpis();
    expect(cmp.kpis()?.down).toBe(1);
    svc['events'].mockReturnValue(of([{ tone: 'ok', title: 'up', detail: null, at: 'now' }]));
    cmp.loadEvents();
    expect(cmp.events().length).toBe(1);
  });

  it('display helpers: kindIcon / statusTone / statusLabel', () => {
    expect(cmp.kindIcon('API')).toBe('bolt');
    expect(cmp.kindIcon('Database')).toBe('server');
    expect(cmp.kindIcon('Cache')).toBe('sliders');
    expect(cmp.kindIcon('Unknown')).toBe('server');
    expect(cmp.statusTone('down')).toBe('danger');
    expect(cmp.statusTone('degraded')).toBe('amber');
    expect(cmp.statusTone('ok')).toBe('green');
    expect(cmp.statusLabel('down')).toBe('sh_status_down');
    expect(cmp.statusLabel('degraded')).toBe('sh_status_degraded');
    expect(cmp.statusLabel('ok')).toBe('sh_status_ok');
  });

  it('ago formats relative minutes', () => {
    expect(cmp.ago(null)).toBe('—');
    expect(cmp.ago(0.5)).toBe('sh_just_now');
    expect(cmp.ago(30)).toBe('30m');
    expect(cmp.ago(120)).toBe('2h');
    expect(cmp.ago(2880)).toBe('2d');
  });

  it('uptime humanizes millis', () => {
    expect(cmp.uptime(null)).toBe('—');
    expect(cmp.uptime(0)).toBe('—');
    expect(cmp.uptime((2 * 86400 + 3 * 3600) * 1000)).toBe('2d 3h');
    expect(cmp.uptime((3 * 3600 + 20 * 60) * 1000)).toBe('3h 20m');
    expect(cmp.uptime(45 * 60 * 1000)).toBe('45m');
  });

  it('metricRows maps an object to ordered key/value rows', () => {
    expect(cmp.metricRows({ a: 1, b: 'x' })).toEqual([{ key: 'a', value: 1 }, { key: 'b', value: 'x' }]);
    expect(cmp.metricRows(null as never)).toEqual([]);
  });

  it('canManage / alertSubsystem / allOperational computeds', () => {
    expect(cmp.canManage()).toBe(true);

    cmp.subsystems.set([sub({ id: 'a', status: 'degraded' }), sub({ id: 'b', status: 'down' })]);
    expect(cmp.alertSubsystem()?.id).toBe('b'); // down beats degraded
    cmp.subsystems.set([sub({ id: 'a', status: 'degraded' }), sub({ id: 'b', status: 'ok' })]);
    expect(cmp.alertSubsystem()?.id).toBe('a');
    cmp.subsystems.set([sub({ status: 'ok' })]);
    expect(cmp.alertSubsystem()).toBeNull();

    expect(cmp.allOperational()).toBe(false); // kpis still null
    cmp.kpis.set({ down: 0, degraded: 0 } as never);
    expect(cmp.allOperational()).toBe(true);
    cmp.kpis.set({ down: 1, degraded: 0 } as never);
    expect(cmp.allOperational()).toBe(false);
  });

  it('openDetail seeds + re-probes the subsystem', () => {
    cmp.openDetail(sub({ id: 's9' }));
    expect(svc['getSubsystem']).toHaveBeenCalledWith('s9');
    expect(cmp.detailOpen()).toBe(true);
    expect(cmp.detail()?.id).toBe('s1'); // from getSubsystem payload
  });

  it('closeDetail / setTab / onEscape manage modal state', () => {
    cmp.openDetail(sub());
    cmp.setTab('checks');
    expect(cmp.modalTab()).toBe('checks');
    cmp.onEscape();
    expect(cmp.detailOpen()).toBe(false);
    expect(cmp.detail()).toBeNull();
  });

  it('probe calls the service when allowed and applies the result', () => {
    cmp.subsystems.set([sub({ id: 's1', status: 'down' })]);
    cmp.probe(sub({ id: 's1' }));
    expect(svc['probe']).toHaveBeenCalledWith('s1');
    expect(cmp.subsystems()[0].status).toBe('ok'); // applied from probe payload
  });

  it('probe is blocked without manage permission', () => {
    // canManage() reads a plain mock fn → memoizes on first read; build a fresh denied component.
    TestBed.resetTestingModule();
    build({ has: () => false });
    cmp.probe(sub({ id: 's1' }));
    expect(svc['probe']).not.toHaveBeenCalled();
  });

  it('flushCache requires confirmation then calls the service', async () => {
    confirm.confirm.mockResolvedValue(false);
    await cmp.flushCache(sub());
    expect(svc['flushCache']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.flushCache(sub());
    expect(svc['flushCache']).toHaveBeenCalled();
  });

  it('toggleMaintenance enables when off and persists the new state', async () => {
    cmp.maintenance.set({ enabled: false, message: null, updatedAt: null });
    await cmp.toggleMaintenance();
    expect(svc['setMaintenance']).toHaveBeenCalledWith(true, null);
    expect(cmp.maintenance()?.enabled).toBe(true);
  });

  it('toggleMaintenance disables when currently on', async () => {
    cmp.maintenance.set({ enabled: true, message: null, updatedAt: null });
    svc['setMaintenance'].mockReturnValue(of({ enabled: false, message: null, updatedAt: null }));
    await cmp.toggleMaintenance();
    expect(svc['setMaintenance']).toHaveBeenCalledWith(false, null);
  });

  it('toggleMaintenance respects a cancelled confirmation', async () => {
    confirm.confirm.mockResolvedValue(false);
    await cmp.toggleMaintenance();
    expect(svc['setMaintenance']).not.toHaveBeenCalled();
  });

  it('toggleMaintenance is blocked without manage permission', async () => {
    TestBed.resetTestingModule();
    build({ has: () => false });
    await cmp.toggleMaintenance();
    expect(svc['setMaintenance']).not.toHaveBeenCalled();
  });
});
