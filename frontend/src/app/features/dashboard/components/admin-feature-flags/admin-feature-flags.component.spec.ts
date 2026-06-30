import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminFeatureFlagsComponent } from './admin-feature-flags.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminFeatureFlagService } from '../../../../core/services/admin-feature-flag.service';
import { AdminOrganizationService } from '../../../../core/services/admin-organization.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

const iso = (offsetDays: number) => new Date(Date.now() + offsetDays * 86_400_000).toISOString();

const flag = (over: Record<string, unknown> = {}) => ({
  id: 'f1', key: 'new_ui', name: 'New UI', description: 'desc', kind: 'RELEASE',
  enabled: true, rollout: 100, audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null,
  audienceOrgName: null, overrides: [], killed: false, archived: false, stale: false,
  status: 'ON', updatedByName: null, updatedAt: iso(0), ...over,
});

const evt = (value: string) => ({ target: { value } } as unknown as Event);
const stopEvt = () => ({ stopPropagation() {} } as Event);

/**
 * Admin Feature Flags console spec: createComponent renders the template (rows empty + editor closed,
 * so no fmt.date is invoked) while the display helpers / computeds / list+editor+action flows are
 * exercised directly. Mirrors the admin-gdpr exemplar (fresh build for the permission-denied path).
 */
describe('AdminFeatureFlagsComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let orgSvc: { list: ReturnType<typeof vi.fn> };
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminFeatureFlagsComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      list: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
      kpis: vi.fn(() => of({ total: 0, on: 0, partial: 0, off: 0, killed: 0, archived: 0, stale: 0, inFlight: 0 })),
      get: vi.fn(() => of({ flag: flag(), history: [] })),
      create: vi.fn(() => of(flag({ id: 'f1' }))),
      update: vi.fn(() => of(flag())),
      enable: vi.fn(() => of(flag({ enabled: true }))),
      disable: vi.fn(() => of(flag({ enabled: false, status: 'OFF' }))),
      kill: vi.fn(() => of(flag({ killed: true, status: 'KILLED' }))),
      restore: vi.fn(() => of(flag({ killed: false, status: 'ON' }))),
      archive: vi.fn(() => of(flag({ archived: true, status: 'ARCHIVED' }))),
      duplicate: vi.fn(() => of(flag({ id: 'f2' }))),
    };
    orgSvc = { list: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })) };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminFeatureFlagsComponent],
      providers: [
        provideRouter([]),
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: FormatService, useValue: {} },
        { provide: AdminFeatureFlagService, useValue: svc },
        { provide: AdminOrganizationService, useValue: orgSvc },
        { provide: AdminIdentityService, useValue: identity },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminFeatureFlagsComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads kpis + list on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['kpis']).toHaveBeenCalled();
    expect(svc['list']).toHaveBeenCalled();
    expect(cmp.updatedAt()).not.toBe('');
  });

  it('kind display helpers: tone / icon / label', () => {
    expect(cmp.kindTone('OPS' as never)).toBe('ops');
    expect(cmp.kindIcon('OPS' as never)).toBe('bolt');
    expect(cmp.kindIcon('EXPERIMENT' as never)).toBe('beaker');
    expect(cmp.kindIcon('PERMISSION' as never)).toBe('key');
    expect(cmp.kindIcon('RELEASE' as never)).toBe('workflow');
    expect(cmp.kindLabel('RELEASE' as never)).toBe('ff_kind_release');
  });

  it('status + audience display helpers', () => {
    expect(cmp.statusTone('KILLED' as never)).toBe('killed');
    expect(cmp.statusDot('ROLLING' as never)).toBe('rolling');
    expect(cmp.statusLabel('ON' as never)).toBe('ff_status_on');
    expect(cmp.audienceLabel('STAFF' as never)).toBe('ff_aud_staff');
  });

  it('targetingSummary resolves each audience kind', () => {
    expect(cmp.targetingSummary(flag({ audience: 'EVERYONE' }) as never)).toBe('ff_aud_everyone');
    expect(cmp.targetingSummary(flag({ audience: 'STAFF' }) as never)).toBe('ff_aud_staff');
    expect(cmp.targetingSummary(flag({ audience: 'ORG', audienceOrgName: 'Acme' }) as never)).toBe('Acme');
    expect(cmp.targetingSummary(flag({ audience: 'PLAN', audiencePlans: ['PRO'] }) as never)).toBe('PRO');
    expect(cmp.targetingSummary(flag({ audience: 'PLAN', audiencePlans: [] }) as never)).toBe('ff_aud_plan');
  });

  it('rolloutLabel reflects status + targeting', () => {
    expect(cmp.rolloutLabel(flag({ status: 'OFF' }) as never)).toBe('ff_rollout_off');
    expect(cmp.rolloutLabel(flag({ status: 'KILLED' }) as never)).toBe('ff_rollout_off');
    expect(cmp.rolloutLabel(flag({ status: 'ON', audience: 'PLAN' }) as never)).toBe('ff_targeted');
    expect(cmp.rolloutLabel(flag({ status: 'ROLLING', audience: 'EVERYONE', rollout: 60 }) as never)).toBe('60%');
  });

  it('relative humanises the timestamp', () => {
    expect(cmp.relative(iso(0))).toBe('ff_ago_today');
    expect(cmp.relative(iso(-1))).toBe('ff_ago_yesterday');
    expect(cmp.relative(iso(-5))).toBe('ff_ago_days');
    expect(cmp.relative(iso(-60))).toBe('ff_ago_months');
  });

  it('draftStatus derives from the editing record + draft', () => {
    cmp.editing.set(null);
    cmp.draft.set({ ...cmp.draft(), enabled: false, rollout: 0 });
    expect(cmp.draftStatus()).toBe('OFF');
    cmp.draft.set({ ...cmp.draft(), enabled: true, rollout: 100, audience: 'EVERYONE' });
    expect(cmp.draftStatus()).toBe('ON');
    cmp.draft.set({ ...cmp.draft(), enabled: true, rollout: 50, audience: 'EVERYONE' });
    expect(cmp.draftStatus()).toBe('ROLLING');
    cmp.editing.set(flag({ killed: true }) as never);
    expect(cmp.draftStatus()).toBe('KILLED');
    cmp.editing.set(flag({ archived: true }) as never);
    expect(cmp.draftStatus()).toBe('ARCHIVED');
  });

  it('canManage / hasFilters / killedRecently / staleCount / editingLocked computeds', () => {
    expect(cmp.canManage()).toBe(true);

    expect(cmp.hasFilters()).toBe(false);
    cmp.kindF.set('OPS');
    expect(cmp.hasFilters()).toBe(true);

    cmp.kpis.set({ killed: 4, stale: 7 } as never);
    expect(cmp.killedRecently()).toBe(4);
    expect(cmp.staleCount()).toBe(7);

    expect(cmp.editingLocked()).toBe(false);
    cmp.editing.set(flag({ killed: true }) as never);
    expect(cmp.editingLocked()).toBe(true);
  });

  it('loadList stores the page payload', () => {
    svc['list'].mockReturnValue(of({ content: [flag()], totalElements: 1, totalPages: 1 }));
    cmp.loadList();
    expect(cmp.rows().length).toBe(1);
    expect(cmp.total()).toBe(1);
    expect(cmp.loading()).toBe(false);
  });

  it('toggleSort sets key, resets page and reloads', () => {
    cmp.page.set(5);
    cmp.toggleSort('rollout');
    expect(cmp.sortKey()).toBe('rollout');
    expect(cmp.page()).toBe(0);
    expect(svc['list']).toHaveBeenCalled();
  });

  it('goToPage clamps to the valid range', () => {
    cmp.totalPages.set(3);
    cmp.goToPage(9);
    expect(cmp.page()).toBe(0);
    cmp.goToPage(2);
    expect(cmp.page()).toBe(2);
  });

  it('clearFilters / inspectKilled / inspectStale reset + reload', () => {
    cmp.search.set('x'); cmp.kindF.set('OPS'); cmp.page.set(3);
    cmp.clearFilters();
    expect(cmp.search()).toBe('');
    expect(cmp.kindF()).toBe('');
    expect(cmp.page()).toBe(0);

    cmp.inspectKilled();
    expect(cmp.statusF()).toBe('KILLED');
    cmp.inspectStale();
    expect(cmp.healthF()).toBe('stale');
    expect(cmp.statusF()).toBe('');
  });

  it('openNew seeds a blank draft and opens the editor (when allowed)', () => {
    cmp.openNew();
    expect(cmp.creating()).toBe(true);
    expect(cmp.editorOpen()).toBe(true);
    expect(cmp.draft().key).toBe('');
    expect(cmp.editing()).toBeNull();
  });

  it('openEdit loads the flag into the draft + fetches history', () => {
    svc['get'].mockReturnValue(of({ flag: flag(), history: [{ label: 'created', actor: 'x', at: iso(0) }] }));
    cmp.openEdit(flag({ id: 'f1', name: 'New UI', overrides: [{ scope: 'u@x', value: 'on' }] }) as never);
    expect(cmp.editing()?.id).toBe('f1');
    expect(cmp.draft().name).toBe('New UI');
    expect(cmp.draft().overrides.length).toBe(1);
    expect(svc['get']).toHaveBeenCalledWith('f1');
    expect(cmp.history().length).toBe(1);
    cmp.closeEditor();
    expect(cmp.editorOpen()).toBe(false);
  });

  it('draft mutators: setKind / setAudience clears / togglePlan / toggleEnabled / onRollout', () => {
    cmp.openNew();
    cmp.setKind('OPS');
    expect(cmp.draft().kind).toBe('OPS');

    cmp.patch('audienceOrgId', 'o1');
    cmp.setAudience('EVERYONE');
    expect(cmp.draft().audienceOrgId).toBeNull();

    cmp.setAudience('PLAN');
    cmp.togglePlan('PRO');
    expect(cmp.hasPlan('PRO')).toBe(true);
    cmp.togglePlan('PRO');
    expect(cmp.hasPlan('PRO')).toBe(false);

    // BLANK draft: enabled false, rollout 0 → toggleEnabled lifts rollout to 100
    cmp.toggleEnabled();
    expect(cmp.draft().enabled).toBe(true);
    expect(cmp.draft().rollout).toBe(100);

    cmp.draft.set({ ...cmp.draft(), enabled: false, rollout: 0 });
    cmp.onRollout(evt('40'));
    expect(cmp.draft().rollout).toBe(40);
    expect(cmp.draft().enabled).toBe(true);
  });

  it('override mutators add / edit / remove rows', () => {
    cmp.openNew();
    cmp.addOverride();
    expect(cmp.draft().overrides.length).toBe(1);
    cmp.setOverrideScope(0, evt('user@x.com'));
    expect(cmp.draft().overrides[0].scope).toBe('user@x.com');
    cmp.setOverrideValue(0, 'off');
    expect(cmp.draft().overrides[0].value).toBe('off');
    cmp.removeOverride(0);
    expect(cmp.draft().overrides.length).toBe(0);
  });

  it('save creates then applies the full draft via update', () => {
    cmp.openNew();
    cmp.draft.set({ ...cmp.draft(), key: 'beta', name: 'Beta' });
    cmp.save();
    expect(svc['create']).toHaveBeenCalled();
    expect(svc['update']).toHaveBeenCalled();
    expect(cmp.busy()).toBe(false);
  });

  it('save updates when editing an existing flag', () => {
    cmp.editing.set(flag({ id: 'f1' }) as never);
    cmp.draft.set({ ...cmp.draft(), name: 'Renamed' });
    cmp.save();
    expect(svc['update']).toHaveBeenCalledWith('f1', expect.anything());
    expect(svc['create']).not.toHaveBeenCalled();
  });

  it('enable / disable / restore delegate to the service', () => {
    cmp.editing.set(flag({ id: 'f1' }) as never);
    cmp.enable();
    expect(svc['enable']).toHaveBeenCalledWith('f1');
    cmp.disable();
    expect(svc['disable']).toHaveBeenCalledWith('f1');
    cmp.restore();
    expect(svc['restore']).toHaveBeenCalledWith('f1');
  });

  it('kill requires confirmation', async () => {
    cmp.editing.set(flag({ id: 'f1' }) as never);
    confirm.confirm.mockResolvedValue(false);
    await cmp.kill();
    expect(svc['kill']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.kill();
    expect(svc['kill']).toHaveBeenCalledWith('f1');
  });

  it('archive requires confirmation', async () => {
    cmp.editing.set(flag({ id: 'f1' }) as never);
    confirm.confirm.mockResolvedValue(false);
    await cmp.archive();
    expect(svc['archive']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.archive();
    expect(svc['archive']).toHaveBeenCalledWith('f1');
  });

  it('duplicate delegates then closes the editor', () => {
    cmp.editing.set(flag({ id: 'f1' }) as never);
    cmp.duplicate();
    expect(svc['duplicate']).toHaveBeenCalledWith('f1');
    expect(cmp.editorOpen()).toBe(false);
  });

  it('row quick actions toggle + restore via the service', () => {
    cmp.rowToggle(flag({ id: 'f1', enabled: false }) as never, stopEvt());
    expect(svc['enable']).toHaveBeenCalledWith('f1');
    cmp.rowRestore(flag({ id: 'f9' }) as never, stopEvt());
    expect(svc['restore']).toHaveBeenCalledWith('f9');
  });

  it('openNew is blocked without manage permission', () => {
    // canManage() reads a plain mock fn (memoizes on first read) → build fresh with the denial.
    TestBed.resetTestingModule();
    build({ has: () => false });
    cmp.openNew();
    expect(cmp.creating()).toBe(false);
    expect(cmp.editorOpen()).toBe(false);
  });

  it('save is blocked without manage permission', () => {
    TestBed.resetTestingModule();
    build({ has: () => false });
    cmp.editing.set(flag({ id: 'f1' }) as never);
    cmp.draft.set({ ...cmp.draft(), name: 'Renamed' });
    cmp.save();
    expect(svc['update']).not.toHaveBeenCalled();
    expect(svc['create']).not.toHaveBeenCalled();
  });
});
