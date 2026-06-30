import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminBackgroundJobsComponent } from './admin-background-jobs.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { AdminBackgroundJobsService } from '../../../../core/services/admin-background-jobs.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

const iso = (offsetMin: number) => new Date(Date.now() + offsetMin * 60_000).toISOString();

const job = (over: Partial<Record<string, unknown>> = {}) => ({
  id: 'j1', name: 'Sync', type: 'Scheduler', scheduleHuman: '*/5', status: 'healthy',
  lastRunAt: null, lastRunOk: null, lastDurationMs: null, nextRunAt: null, itemsLastRun: null,
  runsLast24h: 0, failedLast24h: 0, description: '', drainsQueueId: null, ...over,
}) as never;

/**
 * Admin Background Jobs spec — mirrors the admin-gdpr exemplar: TestBed renders the template,
 * ngOnInit is fired manually, and the display helpers / computeds / load + filter + action flows
 * are exercised directly with the admin service stubbed.
 */
describe('AdminBackgroundJobsComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminBackgroundJobsComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      jobs: vi.fn(() => of([])),
      kpis: vi.fn(() => of({ scheduled: 0, runs24h: 0, failed24h: 0, queueDepth: 0, avgDurationMs: null, nextRunMinutes: null, paused: 0, failing: 0 })),
      queues: vi.fn(() => of([])),
      getJob: vi.fn(() => of({ job: job(), recentRuns: [] })),
      runNow: vi.fn(() => of(job({ status: 'healthy' }))),
      pause: vi.fn(() => of(job({ status: 'paused' }))),
      resume: vi.fn(() => of(job({ status: 'healthy' }))),
    };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminBackgroundJobsComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: FormatService, useValue: {} },
        { provide: AdminIdentityService, useValue: identity },
        { provide: AdminBackgroundJobsService, useValue: svc },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminBackgroundJobsComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads kpis + queues + jobs on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['kpis']).toHaveBeenCalled();
    expect(svc['queues']).toHaveBeenCalled();
    expect(svc['jobs']).toHaveBeenCalled();
    expect(cmp.updatedAt()).not.toBe('');
  });

  it('loadJobs stores rows and clears loading', () => {
    svc['jobs'].mockReturnValue(of([job(), job({ id: 'j2' })]));
    cmp.loadJobs();
    expect(cmp.jobs().length).toBe(2);
    expect(cmp.loading()).toBe(false);
  });

  it('loadQueues / loadKpis store their payloads', () => {
    svc['queues'].mockReturnValue(of([{ id: 'q1', name: 'Q', drainJobId: 'j1', pending: 3, tone: 'clear', breakdown: [] }]));
    cmp.loadQueues();
    expect(cmp.queues().length).toBe(1);
    svc['kpis'].mockReturnValue(of({ scheduled: 7 }) as never);
    cmp.loadKpis();
    expect(cmp.kpis()?.scheduled).toBe(7);
  });

  it('display helpers: kindIcon / statusTone / statusLabel / queueDot', () => {
    expect(cmp.kindIcon('Scheduler')).toBe('clock');
    expect(cmp.kindIcon('Worker')).toBe('refresh');
    expect(cmp.kindIcon('Maintenance')).toBe('shield');
    expect(cmp.kindIcon('Unknown')).toBe('clock');
    expect(cmp.statusTone('failing')).toBe('danger');
    expect(cmp.statusTone('paused')).toBe('amber');
    expect(cmp.statusTone('healthy')).toBe('green');
    expect(cmp.statusLabel('failing')).toBe('bj_status_failing');
    expect(cmp.statusLabel('paused')).toBe('bj_status_paused');
    expect(cmp.statusLabel('healthy')).toBe('bj_status_healthy');
    expect(cmp.queueDot('warn')).toBe('warn');
  });

  it('agoIso / untilIso / duration / nextRunKpi formatting', () => {
    expect(cmp.agoIso(null)).toBe('bj_never');
    expect(cmp.agoIso(iso(-3))).toBe('3m');
    expect(cmp.agoIso(iso(-120))).toBe('2h');
    expect(cmp.agoIso(iso(-2880))).toBe('2d');

    expect(cmp.untilIso(null)).toBe('—');
    expect(cmp.untilIso(iso(-5))).toBe('bj_due');
    expect(cmp.untilIso(iso(30))).toBe('bj_in');

    expect(cmp.duration(null)).toBe('—');
    expect(cmp.duration(500)).toBe('500ms');
    expect(cmp.duration(1500)).toBe('1.5s');

    expect(cmp.nextRunKpi(null)).toBe('—');
    expect(cmp.nextRunKpi(0)).toBe('bj_due');
    expect(cmp.nextRunKpi(30)).toBe('bj_in');
    expect(cmp.nextRunKpi(120)).toBe('bj_in');
  });

  it('canManage / hasActiveFilters / alertJob / alertQueue computeds', () => {
    expect(cmp.canManage()).toBe(true);
    expect(cmp.hasActiveFilters()).toBe(false);
    cmp.search.set('sync');
    expect(cmp.hasActiveFilters()).toBe(true);

    cmp.jobs.set([job({ id: 'ok', status: 'healthy' }), job({ id: 'bad', status: 'failing' })]);
    expect(cmp.alertJob()?.id).toBe('bad');
    cmp.queues.set([{ id: 'q1', tone: 'clear' } as never, { id: 'q2', tone: 'backlog' } as never]);
    expect(cmp.alertQueue()?.id).toBe('q2');
  });

  it('filteredJobs filters by search/type/status/lastRun and sorts', () => {
    cmp.jobs.set([
      job({ id: 'a', name: 'Alpha', type: 'Scheduler', status: 'healthy', lastRunOk: true, lastRunAt: iso(-10) }),
      job({ id: 'b', name: 'Beta', type: 'Worker', status: 'failing', lastRunOk: false, lastRunAt: iso(-5) }),
      job({ id: 'c', name: 'Gamma', type: 'Worker', status: 'paused', lastRunOk: null, lastRunAt: null }),
    ]);
    cmp.search.set('beta');
    expect(cmp.filteredJobs().map(j => j.id)).toEqual(['b']);
    cmp.search.set('');
    cmp.typeFilter.set('Worker');
    expect(cmp.filteredJobs().map(j => j.id).sort()).toEqual(['b', 'c']);
    cmp.typeFilter.set('');
    cmp.statusFilter.set('failing');
    expect(cmp.filteredJobs().map(j => j.id)).toEqual(['b']);
    cmp.statusFilter.set('');
    cmp.lastRunFilter.set('never');
    expect(cmp.filteredJobs().map(j => j.id)).toEqual(['c']);
    cmp.lastRunFilter.set('succeeded');
    expect(cmp.filteredJobs().map(j => j.id)).toEqual(['a']);
    cmp.lastRunFilter.set('failed');
    expect(cmp.filteredJobs().map(j => j.id)).toEqual(['b']);
  });

  it('filter input handlers + clearFilters reset state', () => {
    cmp.onSearch({ target: { value: 'x' } } as unknown as Event);
    cmp.onType({ target: { value: 'Worker' } } as unknown as Event);
    cmp.onStatus({ target: { value: 'paused' } } as unknown as Event);
    cmp.onLastRun({ target: { value: 'failed' } } as unknown as Event);
    expect(cmp.hasActiveFilters()).toBe(true);
    cmp.clearFilters();
    expect(cmp.hasActiveFilters()).toBe(false);
  });

  it('toggleSort flips on the same key and resets on a new key', () => {
    expect(cmp.sortKey()).toBe('status');
    cmp.toggleSort('status'); // same key → flip desc→asc
    expect(cmp.sortDir()).toBe('asc');
    cmp.toggleSort('name'); // new key → asc
    expect(cmp.sortKey()).toBe('name');
    expect(cmp.sortDir()).toBe('asc');
    cmp.toggleSort('lastRun'); // new non-name key → desc
    expect(cmp.sortDir()).toBe('desc');
  });

  it('openDetail seeds + loads detail, openDrainJob resolves the job', () => {
    cmp.openDetail(job({ id: 'j9' }));
    expect(svc['getJob']).toHaveBeenCalledWith('j9');
    expect(cmp.detailOpen()).toBe(true);
    expect(cmp.detail()?.job.id).toBe('j1'); // from getJob payload

    cmp.jobs.set([job({ id: 'drain1' })]);
    cmp.openDrainJob({ id: 'q1', drainJobId: 'drain1' } as never);
    expect(svc['getJob']).toHaveBeenCalledWith('drain1');
  });

  it('closeDetail / setTab / onEscape manage modal state', () => {
    cmp.openDetail(job());
    cmp.setTab('runs');
    expect(cmp.modalTab()).toBe('runs');
    cmp.onEscape();
    expect(cmp.detailOpen()).toBe(false);
    expect(cmp.detail()).toBeNull();
  });

  it('runNow calls the service for a non-destructive job (no confirm)', async () => {
    cmp.jobs.set([job({ id: 'j1' })]);
    await cmp.runNow(job({ id: 'j1', name: 'Sync' }));
    expect(confirm.confirm).not.toHaveBeenCalled();
    expect(svc['runNow']).toHaveBeenCalledWith('j1');
  });

  it('runNow on the retention job requires confirmation', async () => {
    confirm.confirm.mockResolvedValue(false);
    await cmp.runNow(job({ id: 'data-retention' }));
    expect(svc['runNow']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.runNow(job({ id: 'data-retention' }));
    expect(svc['runNow']).toHaveBeenCalledWith('data-retention');
  });

  it('runNow is blocked without manage permission', async () => {
    // canManage() reads a plain mock fn → memoizes on first read; build a fresh denied component.
    TestBed.resetTestingModule();
    build({ has: () => false });
    await cmp.runNow(job({ id: 'j1' }));
    expect(svc['runNow']).not.toHaveBeenCalled();
  });

  it('pause requires confirmation then calls the service + applies the result', async () => {
    cmp.jobs.set([job({ id: 'j1', status: 'healthy' })]);
    confirm.confirm.mockResolvedValue(false);
    await cmp.pause(job({ id: 'j1' }));
    expect(svc['pause']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.pause(job({ id: 'j1' }));
    expect(svc['pause']).toHaveBeenCalledWith('j1');
    expect(cmp.jobs()[0].status).toBe('paused');
  });

  it('resume calls the service without confirmation', () => {
    cmp.resume(job({ id: 'j1' }));
    expect(svc['resume']).toHaveBeenCalledWith('j1');
  });

  it('resume is blocked without manage permission', () => {
    TestBed.resetTestingModule();
    build({ has: () => false });
    cmp.resume(job({ id: 'j1' }));
    expect(svc['resume']).not.toHaveBeenCalled();
  });

  it('toggleMenu opens then closes; onDocClick / onViewportChange clear it', () => {
    const target = { getBoundingClientRect: () => ({ bottom: 10, right: 200 }) };
    const ev = { stopPropagation() {}, currentTarget: target } as unknown as Event;
    cmp.toggleMenu('j1', ev);
    expect(cmp.openMenuId()).toBe('j1');
    cmp.toggleMenu('j1', ev); // same id → close
    expect(cmp.openMenuId()).toBeNull();
    cmp.toggleMenu('j1', ev);
    cmp.onDocClick();
    expect(cmp.openMenuId()).toBeNull();
    cmp.toggleMenu('j1', ev);
    cmp.onViewportChange();
    expect(cmp.openMenuId()).toBeNull();
  });
});
