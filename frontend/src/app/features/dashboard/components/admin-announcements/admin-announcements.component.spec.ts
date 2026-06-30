import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminAnnouncementsComponent } from './admin-announcements.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminAnnouncementService } from '../../../../core/services/admin-announcement.service';
import { AdminOrganizationService } from '../../../../core/services/admin-organization.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

const DAY = 86_400_000;
const iso = (offsetDays: number) => new Date(Date.now() + offsetDays * DAY).toISOString();

const ann = (over: Record<string, unknown> = {}) => ({
  id: 'a1', titleDe: 'Titel', titleEn: 'Title', bodyDe: 'Inhalt', bodyEn: 'Body',
  ctaLabelDe: '', ctaLabelEn: '', ctaUrl: '', type: 'INFO', placement: 'BANNER',
  audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null, audienceOrgName: null,
  dismissible: true, lifecycle: 'PUBLISHED', status: 'LIVE', startsAt: null, endsAt: null,
  createdByName: null, updatedByName: null, updatedAt: iso(0), ...over,
});

/**
 * Admin Announcements console spec: createComponent renders the template (rows empty + editor closed,
 * so no fmt.date is invoked) while the display helpers / computeds / list+editor+action flows are
 * exercised directly. Mirrors the admin-gdpr exemplar (fresh build for the permission-denied path).
 */
describe('AdminAnnouncementsComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let orgSvc: { list: ReturnType<typeof vi.fn> };
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminAnnouncementsComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      list: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
      kpis: vi.fn(() => of({ live: 0, scheduled: 0, drafts: 0, maintenanceLive: 0, expired30d: 0, nextLiveAt: null })),
      get: vi.fn(() => of({ announcement: ann(), history: [] })),
      create: vi.fn(() => of(ann())),
      update: vi.fn(() => of(ann())),
      publish: vi.fn(() => of(ann({ status: 'LIVE' }))),
      end: vi.fn(() => of(ann({ status: 'EXPIRED' }))),
      archive: vi.fn(() => of(ann({ status: 'ARCHIVED' }))),
      duplicate: vi.fn(() => of(ann({ id: 'a2' }))),
    };
    orgSvc = { list: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })) };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminAnnouncementsComponent],
      providers: [
        provideRouter([]),
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: FormatService, useValue: {} },
        { provide: AdminAnnouncementService, useValue: svc },
        { provide: AdminOrganizationService, useValue: orgSvc },
        { provide: AdminIdentityService, useValue: identity },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminAnnouncementsComponent).componentInstance;
  }

  beforeEach(() => build());

  it('loads kpis + list on init', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['kpis']).toHaveBeenCalled();
    expect(svc['list']).toHaveBeenCalled();
    expect(cmp.updatedAt()).not.toBe('');
  });

  it('type display helpers: tone / icon / label', () => {
    expect(cmp.typeTone('MAINTENANCE' as never)).toBe('maintenance');
    expect(cmp.typeIcon('SUCCESS' as never)).toBe('checkCircle');
    expect(cmp.typeIcon('WARNING' as never)).toBe('alertTriangle');
    expect(cmp.typeIcon('MAINTENANCE' as never)).toBe('settings');
    expect(cmp.typeIcon('INFO' as never)).toBe('info');
    expect(cmp.typeLabel('INFO' as never)).toBe('ann_type_info');
    expect(cmp.placementLabel('LOGIN' as never)).toBe('ann_place_login');
    expect(cmp.audienceLabel('STAFF' as never)).toBe('ann_aud_staff');
  });

  it('status display helpers: tone / dot / label / windowState', () => {
    expect(cmp.statusTone('LIVE' as never)).toBe('green');
    expect(cmp.statusTone('SCHEDULED' as never)).toBe('amber');
    expect(cmp.statusTone('EXPIRED' as never)).toBe('slate');
    expect(cmp.statusDot('SCHEDULED' as never)).toBe('scheduled');
    expect(cmp.statusLabel('LIVE' as never)).toBe('ann_status_live');
    expect(cmp.windowState('LIVE' as never)).toBe('live');
    expect(cmp.windowState('EXPIRED' as never)).toBe('expired');
  });

  it('audienceSummary resolves each audience kind', () => {
    expect(cmp.audienceSummary(ann({ audience: 'EVERYONE' }) as never)).toBe('ann_aud_everyone');
    expect(cmp.audienceSummary(ann({ audience: 'STAFF' }) as never)).toBe('ann_aud_staff');
    expect(cmp.audienceSummary(ann({ audience: 'ORG', audienceOrgName: 'Acme' }) as never)).toBe('Acme');
    expect(cmp.audienceSummary(ann({ audience: 'PLAN', audiencePlans: ['PRO', 'STARTER'] }) as never)).toBe('PRO + STARTER');
    expect(cmp.audienceSummary(ann({ audience: 'PLAN', audiencePlans: [] }) as never)).toBe('ann_aud_plan');
  });

  it('windowLabel reflects live / scheduled / expired states', () => {
    expect(cmp.windowLabel(ann({ status: 'LIVE', endsAt: null }) as never)).toBe('ann_open_ended');
    expect(cmp.windowLabel(ann({ status: 'LIVE', endsAt: iso(2) }) as never)).toBe('ann_ends_in');
    expect(cmp.windowLabel(ann({ status: 'SCHEDULED', startsAt: iso(1) }) as never)).toBe('ann_starts_in');
    expect(cmp.windowLabel(ann({ status: 'EXPIRED', endsAt: iso(-3) }) as never)).toBe('ann_ended_rel');
    expect(cmp.windowLabel(ann({ status: 'DRAFT' }) as never)).toBe('—');
  });

  it('relative + localeOk helpers', () => {
    expect(cmp.relative(iso(0))).toBe('ann_ago_today');
    expect(cmp.relative(iso(-1))).toBe('ann_ago_yesterday');
    cmp.draft.set({ ...cmp.draft(), titleDe: 'T', bodyDe: 'B', titleEn: '', bodyEn: '' });
    expect(cmp.localeOk('de')).toBe(true);
    expect(cmp.localeOk('en')).toBe(false);
  });

  it('canManage / hasFilters / banner / editingClosed / canPublish computeds', () => {
    expect(cmp.canManage()).toBe(true);

    expect(cmp.hasFilters()).toBe(false);
    cmp.typeF.set('INFO');
    expect(cmp.hasFilters()).toBe(true);

    cmp.kpis.set({ maintenanceLive: 3 } as never);
    expect(cmp.banner()).toBe(3);
    cmp.alertDismissed.set(true);
    expect(cmp.banner()).toBe(0);

    expect(cmp.editingClosed()).toBe(false);
    cmp.editing.set(ann({ status: 'EXPIRED' }) as never);
    expect(cmp.editingClosed()).toBe(true);

    cmp.draft.set({ ...cmp.draft(), titleDe: 'a', titleEn: 'b', bodyDe: 'c', bodyEn: 'd' });
    expect(cmp.canPublish()).toBe(true);
    cmp.draft.set({ ...cmp.draft(), titleEn: '' });
    expect(cmp.canPublish()).toBe(false);
  });

  it('loadList stores the page payload', () => {
    svc['list'].mockReturnValue(of({ content: [ann()], totalElements: 1, totalPages: 1 }));
    cmp.loadList();
    expect(cmp.rows().length).toBe(1);
    expect(cmp.total()).toBe(1);
    expect(cmp.totalPages()).toBe(1);
    expect(cmp.loading()).toBe(false);
  });

  it('toggleSort sets the key, resets page and reloads', () => {
    cmp.page.set(4);
    cmp.toggleSort('status');
    expect(cmp.sortKey()).toBe('status');
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

  it('clearFilters / inspectBanner reset + reload', () => {
    cmp.search.set('x'); cmp.typeF.set('INFO'); cmp.page.set(3);
    cmp.clearFilters();
    expect(cmp.search()).toBe('');
    expect(cmp.typeF()).toBe('');
    expect(cmp.page()).toBe(0);

    cmp.inspectBanner();
    expect(cmp.typeF()).toBe('MAINTENANCE');
    expect(cmp.statusF()).toBe('LIVE');
  });

  it('openNew seeds a blank draft and opens the editor (when allowed)', () => {
    cmp.openNew();
    expect(cmp.creating()).toBe(true);
    expect(cmp.editorOpen()).toBe(true);
    expect(cmp.draft().titleDe).toBe('');
    expect(cmp.editing()).toBeNull();
  });

  it('openEdit loads the record into the draft + fetches history', () => {
    svc['get'].mockReturnValue(of({ announcement: ann(), history: [{ label: 'created', actor: 'x', at: iso(0) }] }));
    cmp.openEdit(ann({ id: 'a1', titleDe: 'DE', audiencePlans: ['PRO'] }) as never);
    expect(cmp.editing()?.id).toBe('a1');
    expect(cmp.draft().titleDe).toBe('DE');
    expect(svc['get']).toHaveBeenCalledWith('a1');
    expect(cmp.history().length).toBe(1);
    cmp.closeEditor();
    expect(cmp.editorOpen()).toBe(false);
  });

  it('draft mutators: patch / setType / setAudience clears org+plans / togglePlan', () => {
    cmp.openNew();
    cmp.setType('WARNING');
    expect(cmp.draft().type).toBe('WARNING');
    cmp.patch('audienceOrgId', 'o1');
    cmp.setAudience('EVERYONE'); // not ORG nor PLAN → clears both
    expect(cmp.draft().audienceOrgId).toBeNull();
    cmp.setAudience('PLAN');
    cmp.togglePlan('PRO');
    expect(cmp.hasPlan('PRO')).toBe(true);
    cmp.togglePlan('PRO');
    expect(cmp.hasPlan('PRO')).toBe(false);
    cmp.toggleDismissible();
    expect(cmp.draft().dismissible).toBe(false);
  });

  it('dateInputValue / draftStatusPreview', () => {
    expect(cmp.dateInputValue(null)).toBe('');
    expect(cmp.dateInputValue(iso(0))).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);

    cmp.draft.set({ ...cmp.draft(), startsAt: iso(5), endsAt: null });
    expect(cmp.draftStatusPreview()).toBe('ann_preview_scheduled');
    cmp.draft.set({ ...cmp.draft(), startsAt: null, endsAt: iso(-5) });
    expect(cmp.draftStatusPreview()).toBe('ann_preview_expired');
    cmp.draft.set({ ...cmp.draft(), startsAt: null, endsAt: null });
    expect(cmp.draftStatusPreview()).toBe('ann_preview_live');
  });

  it('preview accessors track the active language', () => {
    cmp.draft.set({ ...cmp.draft(), titleDe: 'DE', titleEn: 'EN', bodyDe: 'bDE', bodyEn: 'bEN', ctaLabelDe: 'cDE', ctaLabelEn: 'cEN' });
    cmp.setLang('de');
    expect(cmp.prevTitle()).toBe('DE');
    expect(cmp.prevBody()).toBe('bDE');
    expect(cmp.prevCta()).toBe('cDE');
    cmp.setLang('en');
    expect(cmp.prevTitle()).toBe('EN');
  });

  it('save creates a new record via the service', () => {
    cmp.openNew();
    cmp.draft.set({ ...cmp.draft(), titleDe: 'a', titleEn: 'b', bodyDe: 'c', bodyEn: 'd' });
    cmp.save();
    expect(svc['create']).toHaveBeenCalled();
    expect(cmp.busy()).toBe(false);
  });

  it('save updates when editing an existing record', () => {
    cmp.editing.set(ann({ id: 'a1' }) as never);
    cmp.draft.set({ ...cmp.draft(), titleDe: 'a', titleEn: 'b', bodyDe: 'c', bodyEn: 'd' });
    cmp.save();
    expect(svc['update']).toHaveBeenCalledWith('a1', expect.anything());
  });

  it('publish persists then calls the publish endpoint', () => {
    cmp.editing.set(ann({ id: 'a1' }) as never);
    cmp.draft.set({ ...cmp.draft(), titleDe: 'a', titleEn: 'b', bodyDe: 'c', bodyEn: 'd' });
    cmp.publish();
    expect(svc['update']).toHaveBeenCalledWith('a1', expect.anything());
    expect(svc['publish']).toHaveBeenCalledWith('a1');
  });

  it('end + duplicate delegate to the service', () => {
    cmp.editing.set(ann({ id: 'a1' }) as never);
    cmp.end();
    expect(svc['end']).toHaveBeenCalledWith('a1');

    cmp.editing.set(ann({ id: 'a1' }) as never);
    cmp.duplicate();
    expect(svc['duplicate']).toHaveBeenCalledWith('a1');
    expect(cmp.editorOpen()).toBe(false);
  });

  it('archive requires confirmation', async () => {
    cmp.editing.set(ann({ id: 'a1' }) as never);
    confirm.confirm.mockResolvedValue(false);
    await cmp.archive();
    expect(svc['archive']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.archive();
    expect(svc['archive']).toHaveBeenCalledWith('a1');
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
    cmp.editing.set(ann({ id: 'a1' }) as never);
    cmp.draft.set({ ...cmp.draft(), titleDe: 'a', titleEn: 'b', bodyDe: 'c', bodyEn: 'd' });
    cmp.save();
    expect(svc['update']).not.toHaveBeenCalled();
    expect(svc['create']).not.toHaveBeenCalled();
  });
});
