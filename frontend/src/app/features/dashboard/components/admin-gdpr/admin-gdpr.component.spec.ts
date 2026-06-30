import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminGdprComponent } from './admin-gdpr.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { AdminGdprService } from '../../../../core/services/admin-gdpr.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

const DAY = 86_400_000;
const iso = (offsetDays: number) => new Date(Date.now() + offsetDays * DAY).toISOString();

/**
 * Exemplar admin-console spec: the component renders via TestBed (ngOnInit fires → its admin service
 * is stubbed to return observables) and the rich display helpers / computeds / list+action flows are
 * exercised directly. New admin-component specs should follow this shape.
 */
describe('AdminGdprComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminGdprComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      requests: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
      kpis: vi.fn(() => of({ overdue: 0, dueSoon: 0 })),
      retention: vi.fn(() => of({ lastSweepAt: null })),
      getRequest: vi.fn(() => of({ request: { id: 'r1' }, footprint: {}, timeline: [] })),
      runExport: vi.fn(() => of({ id: 'r1', status: 'IN_PROGRESS' })),
      executeErasure: vi.fn(() => of({ id: 'r1', status: 'COMPLETED' })),
      markComplete: vi.fn(() => of({ id: 'r1', status: 'COMPLETED' })),
      reject: vi.fn(() => of({ id: 'r1', status: 'REJECTED' })),
      create: vi.fn(() => of({ id: 'r2' })),
    };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminGdprComponent],
      providers: [
        provideRouter([]),
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: FormatService, useValue: {} },
        { provide: AdminIdentityService, useValue: identity },
        { provide: AdminGdprService, useValue: svc },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminGdprComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads kpis + requests on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['requests']).toHaveBeenCalled();
    expect(svc['kpis']).toHaveBeenCalled();
  });

  it('display helpers: initial / isOpen / deadlineState / type+status labels', () => {
    expect(cmp.initial('alice')).toBe('A');
    expect(cmp.initial('')).toBe('?');
    expect(cmp.isOpen({ status: 'PENDING' } as never)).toBe(true);
    expect(cmp.isOpen({ status: 'COMPLETED' } as never)).toBe(false);
    expect(cmp.deadlineState({ deadlineAt: iso(-1) } as never)).toBe('overdue');
    expect(cmp.deadlineState({ deadlineAt: iso(30) } as never)).toBe('ok');
    expect(cmp.typeLabel('EXPORT' as never)).toBe('gdpr_type_export');
    expect(cmp.typeIcon('ERASURE' as never)).toBe('trash');
    expect(cmp.statusLabel('IN_PROGRESS' as never)).toBe('gdpr_status_inprogress');
  });

  it('avatarHue is a deterministic 0..359 hue', () => {
    const h = cmp.avatarHue('subject@x.com');
    expect(h).toBeGreaterThanOrEqual(0);
    expect(h).toBeLessThan(360);
    expect(cmp.avatarHue('subject@x.com')).toBe(h);
  });

  it('canManage / hasFilters / banner / cFormValid computeds', () => {
    expect(cmp.canManage()).toBe(true);
    expect(cmp.hasFilters()).toBe(false);
    cmp.search.set('x');
    expect(cmp.hasFilters()).toBe(true);

    cmp.kpis.set({ overdue: 2, dueSoon: 5 } as never);
    expect(cmp.banner()).toEqual({ kind: 'danger', n: 2 });
    cmp.kpis.set({ overdue: 0, dueSoon: 5 } as never);
    expect(cmp.banner()).toEqual({ kind: 'warn', n: 5 });

    cmp.cForm.set({ subjectEmail: 'bad', subjectName: '', type: 'EXPORT', channel: 'EMAIL', note: '' });
    expect(cmp.cFormValid()).toBe(false);
    cmp.cForm.set({ subjectEmail: 'a@b.c', subjectName: 'Alice', type: 'EXPORT', channel: 'EMAIL', note: '' });
    expect(cmp.cFormValid()).toBe(true);
  });

  it('loadRequests stores the page payload', () => {
    svc['requests'].mockReturnValue(of({ content: [{ id: 'r1' }], totalElements: 1, totalPages: 1 }));
    cmp.loadRequests();
    expect(cmp.rows().length).toBe(1);
    expect(cmp.total()).toBe(1);
    expect(cmp.loading()).toBe(false);
  });

  it('toggleSort flips direction on the same key and resets on a new key', () => {
    cmp.toggleSort('deadline'); // same as default key → flip to desc
    expect(cmp.sortDir()).toBe('desc');
    cmp.toggleSort('status'); // new key → asc
    expect(cmp.sortKey()).toBe('status');
    expect(cmp.sortDir()).toBe('asc');
  });

  it('goToPage clamps to the valid range', () => {
    cmp.totalPages.set(3);
    cmp.goToPage(5);
    expect(cmp.page()).toBe(0);
    cmp.goToPage(2);
    expect(cmp.page()).toBe(2);
  });

  it('openRequest seeds + loads the detail', () => {
    cmp.openRequest({ id: 'r1', subjectName: 'A' } as never);
    expect(svc['getRequest']).toHaveBeenCalledWith('r1');
    expect(cmp.detail()?.request.id).toBe('r1');
  });

  it('runExport calls the service when allowed', () => {
    cmp.runExport({ id: 'r1', subjectName: 'A' } as never);
    expect(svc['runExport']).toHaveBeenCalledWith('r1');
  });

  it('runExport is blocked without manage permission', () => {
    // canManage() reads a plain mock fn (not a signal) → it memoizes on first read, so the
    // blocked case needs a fresh component built with the permission denied from the start.
    TestBed.resetTestingModule();
    build({ has: () => false });
    cmp.runExport({ id: 'r1', subjectName: 'A' } as never);
    expect(svc['runExport']).not.toHaveBeenCalled();
  });

  it('executeErasure requires confirmation', async () => {
    confirm.confirm.mockResolvedValue(false);
    await cmp.executeErasure({ id: 'r1', subjectName: 'A' } as never);
    expect(svc['executeErasure']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.executeErasure({ id: 'r1', subjectName: 'A' } as never);
    expect(svc['executeErasure']).toHaveBeenCalledWith('r1');
  });

  it('confirmReject needs a reason', () => {
    cmp.rejecting.set({ id: 'r1' } as never);
    cmp.rejectReason.set('   ');
    cmp.confirmReject();
    expect(svc['reject']).not.toHaveBeenCalled();
    cmp.rejectReason.set('not verified');
    cmp.confirmReject();
    expect(svc['reject']).toHaveBeenCalledWith('r1', 'not verified');
  });

  it('submitCreate is gated on a valid form', () => {
    cmp.cForm.set({ subjectEmail: 'bad', subjectName: '', type: 'EXPORT', channel: 'EMAIL', note: '' });
    cmp.submitCreate();
    expect(svc['create']).not.toHaveBeenCalled();
    cmp.cForm.set({ subjectEmail: 'a@b.c', subjectName: 'Alice', type: 'EXPORT', channel: 'EMAIL', note: ' n ' });
    cmp.submitCreate();
    expect(svc['create']).toHaveBeenCalled();
  });
});
