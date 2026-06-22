import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminAnnouncementService } from '../../../../core/services/admin-announcement.service';
import { AdminOrganizationService } from '../../../../core/services/admin-organization.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import {
  Announcement,
  AnnouncementHistoryEntry,
  AnnouncementKpis,
  AnnouncementType,
  AnnouncementPlacement,
  AnnouncementAudience,
  AnnouncementStatus,
  AnnouncementFilters,
  AnnouncementRequest,
} from '../../../../models/admin-announcement.model';

type EditorTab = 'editor' | 'history';
type Lang = 'de' | 'en';

const BLANK: AnnouncementRequest = {
  titleDe: '', titleEn: '', bodyDe: '', bodyEn: '', ctaLabelDe: '', ctaLabelEn: '', ctaUrl: '',
  type: 'INFO', placement: 'BANNER', audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null,
  dismissible: true, startsAt: null, endsAt: null,
};

/**
 * Platform-staff Announcements console: the announcement queue + bilingual editor with a live banner
 * preview. Read + mutate gate on ANNOUNCEMENT_MANAGE.
 */
@Component({
  selector: 'app-admin-announcements',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-announcements.component.html',
  styleUrl: './admin-announcements.component.scss',
})
export class AdminAnnouncementsComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private service = inject(AdminAnnouncementService);
  private orgService = inject(AdminOrganizationService);
  private confirmDialog = inject(ConfirmDialogService);
  private timers = managedTimers();

  // ── List state ──────────────────────────────────────────────────────────────
  rows = signal<Announcement[]>([]);
  total = signal(0);
  totalPages = signal(0);
  page = signal(0);
  loading = signal(true);
  error = signal('');
  kpis = signal<AnnouncementKpis | null>(null);
  refreshing = signal(false);
  updatedAt = signal('');
  alertDismissed = signal(false);
  busy = signal(false);
  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Filters / sort ──────────────────────────────────────────────────────────
  search = signal('');
  typeF = signal<'' | AnnouncementType>('');
  statusF = signal<'' | AnnouncementStatus>('');
  audienceF = signal<'' | AnnouncementAudience>('');
  placementF = signal<'' | AnnouncementPlacement>('');
  sortKey = signal<'window' | 'updated' | 'status'>('window');

  // ── Editor ────────────────────────────────────────────────────────────────────
  editing = signal<Announcement | null>(null); // saved record (null = unsaved new)
  creating = signal(false);                     // modal open as new
  editorOpen = computed(() => this.creating() || this.editing() !== null);
  draft = signal<AnnouncementRequest>({ ...BLANK });
  lang = signal<Lang>('de');
  editorTab = signal<EditorTab>('editor');
  history = signal<AnnouncementHistoryEntry[]>([]);
  detailFlash = signal('');
  private detailFlashTimer: ReturnType<typeof setTimeout> | null = null;

  // org picker
  orgQuery = signal('');
  orgResults = signal<{ id: string; name: string }[]>([]);
  orgPickerOpen = signal(false);
  selectedOrgName = signal<string | null>(null);

  readonly pageSize = 10;
  readonly types: AnnouncementType[] = ['INFO', 'SUCCESS', 'WARNING', 'MAINTENANCE'];
  readonly placements: AnnouncementPlacement[] = ['BANNER', 'TOAST', 'LOGIN'];
  readonly audiences: AnnouncementAudience[] = ['EVERYONE', 'PLAN', 'ORG', 'STAFF'];
  readonly planOptions = ['STARTER', 'PRO', 'ENTERPRISE'];

  readonly canManage = computed(() => this.identity.has('ANNOUNCEMENT_MANAGE'));
  readonly hasFilters = computed(() => !!this.search() || !!this.typeF() || !!this.statusF() || !!this.audienceF() || !!this.placementF());
  readonly banner = computed(() => (this.kpis()?.maintenanceLive ?? 0) > 0 && !this.alertDismissed() ? this.kpis()!.maintenanceLive : 0);
  readonly editingClosed = computed(() => { const e = this.editing(); return !!e && (e.status === 'EXPIRED' || e.status === 'ARCHIVED'); });
  readonly canPublish = computed(() => { const d = this.draft(); return !!(d.titleDe.trim() && d.titleEn.trim() && d.bodyDe.trim() && d.bodyEn.trim()); });

  ngOnInit() {
    this.loadKpis();
    this.stampUpdated();
    this.loadList();
  }

  private filters(): AnnouncementFilters {
    return { search: this.search() || undefined, type: this.typeF() || undefined, status: this.statusF() || undefined,
      audience: this.audienceF() || undefined, placement: this.placementF() || undefined, sort: this.sortKey() };
  }
  loadList() {
    this.loading.set(true); this.error.set('');
    this.service.list(this.filters(), this.page(), this.pageSize).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.totalElements); this.totalPages.set(p.totalPages); this.loading.set(false); },
      error: () => { this.rows.set([]); this.loading.set(false); this.error.set(this.i18n.t('ann_load_failed')); },
    });
  }
  loadKpis() { this.service.kpis().subscribe({ next: k => this.kpis.set(k), error: () => {} }); }

  private stampUpdated() { const d = new Date(); const p = (n: number) => String(n).padStart(2, '0'); this.updatedAt.set(`${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`); }
  refresh() { if (this.refreshing()) return; this.refreshing.set(true); this.loadKpis(); this.loadList(); this.stampUpdated(); this.timers.set(() => this.refreshing.set(false), 600); }

  // ── Filters ──────────────────────────────────────────────────────────────────
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  onSearch(e: Event) { this.search.set((e.target as HTMLInputElement).value); if (this.searchTimer) this.timers.clear(this.searchTimer); this.searchTimer = this.timers.set(() => { this.page.set(0); this.loadList(); }, 400); }
  onType(e: Event) { this.typeF.set((e.target as HTMLSelectElement).value as '' | AnnouncementType); this.page.set(0); this.loadList(); }
  onStatus(e: Event) { this.statusF.set((e.target as HTMLSelectElement).value as '' | AnnouncementStatus); this.page.set(0); this.loadList(); }
  onAudience(e: Event) { this.audienceF.set((e.target as HTMLSelectElement).value as '' | AnnouncementAudience); this.page.set(0); this.loadList(); }
  onPlacement(e: Event) { this.placementF.set((e.target as HTMLSelectElement).value as '' | AnnouncementPlacement); this.page.set(0); this.loadList(); }
  toggleSort(key: 'window' | 'updated' | 'status') { this.sortKey.set(key); this.page.set(0); this.loadList(); }
  clearFilters() { this.search.set(''); this.typeF.set(''); this.statusF.set(''); this.audienceF.set(''); this.placementF.set(''); this.page.set(0); this.loadList(); }
  goToPage(p: number) { if (p < 0 || p >= this.totalPages()) return; this.page.set(p); this.loadList(); }
  inspectBanner() { this.typeF.set('MAINTENANCE'); this.statusF.set('LIVE'); this.page.set(0); this.loadList(); }

  // ── Display helpers ──────────────────────────────────────────────────────────
  typeTone(t: AnnouncementType): string { return t.toLowerCase(); }
  typeIcon(t: AnnouncementType): string { return t === 'SUCCESS' ? 'checkCircle' : t === 'WARNING' ? 'alertTriangle' : t === 'MAINTENANCE' ? 'settings' : 'info'; }
  typeLabel(t: AnnouncementType): string { return this.i18n.t('ann_type_' + t.toLowerCase()); }
  placementLabel(p: AnnouncementPlacement): string { return this.i18n.t('ann_place_' + p.toLowerCase()); }
  audienceLabel(a: AnnouncementAudience): string { return this.i18n.t('ann_aud_' + a.toLowerCase()); }
  statusTone(s: AnnouncementStatus): string { return s === 'LIVE' ? 'green' : s === 'SCHEDULED' ? 'amber' : 'slate'; }
  statusDot(s: AnnouncementStatus): string { return s === 'LIVE' ? 'live' : s === 'SCHEDULED' ? 'scheduled' : 'expired'; }
  statusLabel(s: AnnouncementStatus): string { return this.i18n.t('ann_status_' + s.toLowerCase()); }
  windowState(s: AnnouncementStatus): string { return s === 'LIVE' ? 'live' : s === 'SCHEDULED' ? 'scheduled' : 'expired'; }
  audienceSummary(a: Announcement): string {
    if (a.audience === 'EVERYONE') return this.i18n.t('ann_aud_everyone');
    if (a.audience === 'STAFF') return this.i18n.t('ann_aud_staff');
    if (a.audience === 'ORG') return a.audienceOrgName ?? this.i18n.t('ann_aud_org');
    if (a.audience === 'PLAN') return a.audiencePlans.length ? a.audiencePlans.join(' + ') : this.i18n.t('ann_aud_plan');
    return '—';
  }
  windowLabel(a: Announcement): string {
    if (a.status === 'LIVE') return a.endsAt ? this.i18n.t('ann_ends_in', { rel: this.relFuture(a.endsAt) }) : this.i18n.t('ann_open_ended');
    if (a.status === 'SCHEDULED') return this.i18n.t('ann_starts_in', { rel: this.relFuture(a.startsAt) });
    if (a.status === 'EXPIRED') return this.i18n.t('ann_ended_rel', { rel: this.relPast(a.endsAt) });
    return '—';
  }
  private relFuture(at: string | null): string {
    if (!at) return '—';
    const mins = Math.round((new Date(at).getTime() - Date.now()) / 60000);
    if (mins <= 0) return this.i18n.t('ann_rel_moments');
    if (mins < 60) return mins + 'min'; if (mins < 1440) return Math.round(mins / 60) + 'h';
    return Math.round(mins / 1440) + 'd';
  }
  private relPast(at: string | null): string {
    if (!at) return '—';
    const days = Math.floor((Date.now() - new Date(at).getTime()) / 86400000);
    if (days <= 0) return this.i18n.t('ann_ago_today'); if (days === 1) return this.i18n.t('ann_ago_yesterday');
    if (days < 30) return this.i18n.t('ann_ago_days', { n: days + '' }); return this.i18n.t('ann_ago_months', { n: Math.round(days / 30) + '' });
  }
  relative(at: string): string { return this.relPast(at); }
  localeOk(lang: Lang): boolean { const d = this.draft(); return lang === 'de' ? !!(d.titleDe.trim() && d.bodyDe.trim()) : !!(d.titleEn.trim() && d.bodyEn.trim()); }

  // ── Editor open/close ─────────────────────────────────────────────────────────
  openNew() {
    if (!this.canManage()) return;
    this.editing.set(null); this.creating.set(true); this.draft.set({ ...BLANK });
    this.selectedOrgName.set(null); this.lang.set('de'); this.editorTab.set('editor'); this.history.set([]); this.detailFlash.set('');
  }
  openEdit(a: Announcement) {
    this.creating.set(false); this.editing.set(a); this.lang.set('de'); this.editorTab.set('editor'); this.detailFlash.set('');
    this.draft.set({
      titleDe: a.titleDe, titleEn: a.titleEn, bodyDe: a.bodyDe ?? '', bodyEn: a.bodyEn ?? '',
      ctaLabelDe: a.ctaLabelDe ?? '', ctaLabelEn: a.ctaLabelEn ?? '', ctaUrl: a.ctaUrl ?? '',
      type: a.type, placement: a.placement, audience: a.audience, audiencePlans: [...a.audiencePlans],
      audienceOrgId: a.audienceOrgId, dismissible: a.dismissible, startsAt: a.startsAt, endsAt: a.endsAt,
    });
    this.selectedOrgName.set(a.audienceOrgName);
    this.history.set([]);
    this.service.get(a.id).subscribe({ next: d => this.history.set(d.history), error: () => {} });
  }
  closeEditor() { this.creating.set(false); this.editing.set(null); this.orgPickerOpen.set(false); }
  setLang(l: Lang) { this.lang.set(l); }
  setEditorTab(t: EditorTab) { this.editorTab.set(t); }

  // ── Draft mutators ──────────────────────────────────────────────────────────
  patch<K extends keyof AnnouncementRequest>(key: K, value: AnnouncementRequest[K]) { this.draft.update(d => ({ ...d, [key]: value })); }
  patchInput<K extends keyof AnnouncementRequest>(key: K, e: Event) { this.patch(key, (e.target as HTMLInputElement | HTMLTextAreaElement).value as AnnouncementRequest[K]); }
  setType(t: AnnouncementType) { this.patch('type', t); }
  setPlacement(p: AnnouncementPlacement) { this.patch('placement', p); }
  setAudience(a: AnnouncementAudience) { this.patch('audience', a); if (a !== 'ORG') { this.patch('audienceOrgId', null); this.selectedOrgName.set(null); } if (a !== 'PLAN') this.patch('audiencePlans', []); }
  togglePlan(p: string) { this.draft.update(d => ({ ...d, audiencePlans: d.audiencePlans.includes(p) ? d.audiencePlans.filter(x => x !== p) : [...d.audiencePlans, p] })); }
  hasPlan(p: string): boolean { return this.draft().audiencePlans.includes(p); }
  toggleDismissible() { this.patch('dismissible', !this.draft().dismissible); }
  onDateInput(key: 'startsAt' | 'endsAt', e: Event) { const v = (e.target as HTMLInputElement).value; this.patch(key, v ? new Date(v).toISOString() : null); }
  dateInputValue(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso); const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
  }
  draftStatusPreview(): string {
    const d = this.draft();
    if (d.startsAt && new Date(d.startsAt).getTime() > Date.now()) return this.i18n.t('ann_preview_scheduled', { rel: this.relFuture(d.startsAt) });
    if (d.endsAt && new Date(d.endsAt).getTime() < Date.now()) return this.i18n.t('ann_preview_expired');
    return this.i18n.t('ann_preview_live');
  }

  // org picker
  private orgTimer: ReturnType<typeof setTimeout> | null = null;
  onOrgSearch(e: Event) {
    const q = (e.target as HTMLInputElement).value; this.orgQuery.set(q); this.orgPickerOpen.set(true);
    if (this.orgTimer) this.timers.clear(this.orgTimer);
    this.orgTimer = this.timers.set(() => this.orgService.list(q, false, 0, 8).subscribe({
      next: p => this.orgResults.set(p.content.map(o => ({ id: o.id, name: o.name }))), error: () => this.orgResults.set([]),
    }), 300);
  }
  selectOrg(o: { id: string; name: string }) { this.patch('audienceOrgId', o.id); this.selectedOrgName.set(o.name); this.orgPickerOpen.set(false); this.orgQuery.set(''); }

  // ── Preview accessors (current lang) ────────────────────────────────────────
  prevTitle(): string { const d = this.draft(); return (this.lang() === 'de' ? d.titleDe : d.titleEn) || ''; }
  prevBody(): string { const d = this.draft(); return (this.lang() === 'de' ? d.bodyDe : d.bodyEn) || ''; }
  prevCta(): string { const d = this.draft(); return (this.lang() === 'de' ? d.ctaLabelDe : d.ctaLabelEn) || ''; }

  // ── Actions ───────────────────────────────────────────────────────────────────
  private flashMsg(m: string) { this.flash.set(m); if (this.flashTimer) this.timers.clear(this.flashTimer); this.flashTimer = this.timers.set(() => this.flash.set(''), 3200); }
  private detailFlashMsg(m: string) { this.detailFlash.set(m); if (this.detailFlashTimer) this.timers.clear(this.detailFlashTimer); this.detailFlashTimer = this.timers.set(() => this.detailFlash.set(''), 2600); }
  private reqBody(): AnnouncementRequest { return { ...this.draft() }; }

  /** Save the current draft; returns the saved record id via callback. */
  private persist(then: (a: Announcement) => void) {
    const e = this.editing();
    const obs = e ? this.service.update(e.id, this.reqBody()) : this.service.create(this.reqBody());
    this.busy.set(true);
    obs.subscribe({
      next: a => { this.busy.set(false); this.editing.set(a); this.creating.set(false); this.applyRow(a); then(a); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('ann_action_failed')); },
    });
  }
  private applyRow(a: Announcement) { this.rows.update(list => { const i = list.findIndex(x => x.id === a.id); return i >= 0 ? list.map(x => x.id === a.id ? a : x) : [a, ...list]; }); this.loadKpis(); }

  save() { if (!this.canManage() || this.busy()) return; this.persist(() => { this.detailFlashMsg(this.i18n.t('ann_flash_saved')); this.flashMsg(this.i18n.t('ann_flash_saved')); }); }
  publish() {
    if (!this.canManage() || this.busy() || !this.canPublish()) return;
    this.persist(a => this.service.publish(a.id).subscribe({
      next: u => { this.applyRow(u); this.editing.set(u); this.detailFlashMsg(this.i18n.t('ann_flash_published', { t: u.titleEn })); this.flashMsg(this.i18n.t('ann_flash_published', { t: u.titleEn })); },
      error: () => this.error.set(this.i18n.t('ann_action_failed')),
    }));
  }
  end() {
    const e = this.editing(); if (!e || !this.canManage() || this.busy()) return;
    this.busy.set(true);
    this.service.end(e.id).subscribe({ next: u => { this.busy.set(false); this.editing.set(u); this.applyRow(u); this.detailFlashMsg(this.i18n.t('ann_flash_ended')); }, error: () => { this.busy.set(false); this.error.set(this.i18n.t('ann_action_failed')); } });
  }
  async archive() {
    const e = this.editing(); if (!e || !this.canManage()) return;
    const ok = await this.confirmDialog.confirm({ title: this.i18n.t('ann_archive_title'), message: this.i18n.t('ann_archive_msg', { t: e.titleEn }), confirmText: this.i18n.t('ann_archive'), cancelText: this.i18n.t('confirm_cancel'), tone: 'danger' });
    if (!ok) return;
    this.busy.set(true);
    this.service.archive(e.id).subscribe({ next: u => { this.busy.set(false); this.editing.set(u); this.applyRow(u); this.detailFlashMsg(this.i18n.t('ann_flash_archived')); }, error: () => { this.busy.set(false); this.error.set(this.i18n.t('ann_action_failed')); } });
  }
  duplicate() {
    const e = this.editing(); if (!e || !this.canManage() || this.busy()) return;
    this.busy.set(true);
    this.service.duplicate(e.id).subscribe({ next: u => { this.busy.set(false); this.applyRow(u); this.flashMsg(this.i18n.t('ann_flash_duplicated')); this.closeEditor(); }, error: () => { this.busy.set(false); this.error.set(this.i18n.t('ann_action_failed')); } });
  }

  // row-menu quick actions
  rowPublish(a: Announcement, e: Event) { e.stopPropagation(); this.openEdit(a); this.timers.set(() => this.publish(), 0); this.openMenuId.set(null); }
  rowArchive(a: Announcement, e: Event) { e.stopPropagation(); this.openEdit(a); this.timers.set(() => this.archive(), 0); this.openMenuId.set(null); }
  rowDuplicate(a: Announcement, e: Event) { e.stopPropagation(); this.openEdit(a); this.timers.set(() => this.duplicate(), 0); this.openMenuId.set(null); }

  // ── Row menu ──────────────────────────────────────────────────────────────────
  openMenuId = signal<string | null>(null);
  menuPos = signal<{ top: number; left: number } | null>(null);
  toggleMenu(id: string, event: Event) {
    event.stopPropagation();
    if (this.openMenuId() === id) { this.openMenuId.set(null); return; }
    const r = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.menuPos.set({ top: r.bottom + 4, left: Math.max(8, r.right - 192) });
    this.openMenuId.set(id);
  }

  @HostListener('document:keydown.escape')
  onEscape() { if (this.orgPickerOpen()) { this.orgPickerOpen.set(false); return; } if (this.editorOpen()) this.closeEditor(); }
  @HostListener('document:click')
  onDocClick() { this.openMenuId.set(null); }
  @HostListener('window:scroll')
  @HostListener('window:resize')
  onViewport() { this.openMenuId.set(null); }
}
