import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminFeatureFlagService } from '../../../../core/services/admin-feature-flag.service';
import { AdminOrganizationService } from '../../../../core/services/admin-organization.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import {
  FeatureFlag,
  FlagHistoryEntry,
  FeatureFlagKpis,
  FeatureFlagKind,
  FlagAudience,
  FlagStatus,
  FlagOverride,
  FlagFilters,
} from '../../../../models/admin-feature-flag.model';

type EditorTab = 'rollout' | 'targeting' | 'history';

interface FlagDraft {
  key: string;
  name: string;
  description: string;
  kind: FeatureFlagKind;
  enabled: boolean;
  rollout: number;
  audience: FlagAudience;
  audiencePlans: string[];
  audienceOrgId: string | null;
  overrides: FlagOverride[];
}
const BLANK: FlagDraft = { key: '', name: '', description: '', kind: 'RELEASE', enabled: false, rollout: 0, audience: 'EVERYONE', audiencePlans: [], audienceOrgId: null, overrides: [] };

/**
 * Platform-staff Feature Flags console: the flag list + editor (rollout slider / targeting+overrides
 * / history) with kill-switch. Read + mutate gate on FEATURE_FLAG_MANAGE.
 */
@Component({
  selector: 'app-admin-feature-flags',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-feature-flags.component.html',
  styleUrl: './admin-feature-flags.component.scss',
})
export class AdminFeatureFlagsComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private service = inject(AdminFeatureFlagService);
  private orgService = inject(AdminOrganizationService);
  private confirmDialog = inject(ConfirmDialogService);
  private timers = managedTimers();

  // ── List state ──────────────────────────────────────────────────────────────
  rows = signal<FeatureFlag[]>([]);
  total = signal(0);
  totalPages = signal(0);
  page = signal(0);
  loading = signal(true);
  error = signal('');
  kpis = signal<FeatureFlagKpis | null>(null);
  refreshing = signal(false);
  updatedAt = signal('');
  alertDismissed = signal(false);
  busy = signal(false);
  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Filters / sort ──────────────────────────────────────────────────────────
  search = signal('');
  kindF = signal<'' | FeatureFlagKind>('');
  statusF = signal<'' | FlagStatus>('');
  targetingF = signal<'' | FlagAudience>('');
  healthF = signal<'' | 'stale'>('');
  sortKey = signal<'updated' | 'rollout' | 'status'>('updated');

  // ── Editor ──────────────────────────────────────────────────────────────────
  editing = signal<FeatureFlag | null>(null);
  creating = signal(false);
  editorOpen = computed(() => this.creating() || this.editing() !== null);
  draft = signal<FlagDraft>({ ...BLANK });
  editorTab = signal<EditorTab>('rollout');
  history = signal<FlagHistoryEntry[]>([]);
  detailFlash = signal('');
  private detailFlashTimer: ReturnType<typeof setTimeout> | null = null;

  // org picker
  orgQuery = signal('');
  orgResults = signal<{ id: string; name: string }[]>([]);
  orgPickerOpen = signal(false);
  selectedOrgName = signal<string | null>(null);

  readonly pageSize = 10;
  readonly kinds: FeatureFlagKind[] = ['RELEASE', 'OPS', 'EXPERIMENT', 'PERMISSION'];
  readonly audiences: FlagAudience[] = ['EVERYONE', 'PLAN', 'ORG', 'STAFF'];
  readonly planOptions = ['STARTER', 'PRO', 'ENTERPRISE'];

  readonly canManage = computed(() => this.identity.has('FEATURE_FLAG_MANAGE'));
  readonly hasFilters = computed(() => !!this.search() || !!this.kindF() || !!this.statusF() || !!this.targetingF() || !!this.healthF());
  readonly killedRecently = computed(() => this.kpis()?.killed ?? 0);
  readonly staleCount = computed(() => this.kpis()?.stale ?? 0);
  readonly editingLocked = computed(() => { const e = this.editing(); return !!e && (e.killed || e.archived); });

  ngOnInit() { this.loadKpis(); this.stampUpdated(); this.loadList(); }

  private filters(): FlagFilters {
    return { search: this.search() || undefined, kind: this.kindF() || undefined, status: this.statusF() || undefined,
      targeting: this.targetingF() || undefined, health: this.healthF() || undefined, sort: this.sortKey() };
  }
  loadList() {
    this.loading.set(true); this.error.set('');
    this.service.list(this.filters(), this.page(), this.pageSize).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.totalElements); this.totalPages.set(p.totalPages); this.loading.set(false); },
      error: () => { this.rows.set([]); this.loading.set(false); this.error.set(this.i18n.t('ff_load_failed')); },
    });
  }
  loadKpis() { this.service.kpis().subscribe({ next: k => this.kpis.set(k), error: () => {} }); }
  private stampUpdated() { const d = new Date(); const p = (n: number) => String(n).padStart(2, '0'); this.updatedAt.set(`${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`); }
  refresh() { if (this.refreshing()) return; this.refreshing.set(true); this.loadKpis(); this.loadList(); this.stampUpdated(); this.timers.set(() => this.refreshing.set(false), 600); }

  // ── Filters ──────────────────────────────────────────────────────────────────
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  onSearch(e: Event) { this.search.set((e.target as HTMLInputElement).value); if (this.searchTimer) this.timers.clear(this.searchTimer); this.searchTimer = this.timers.set(() => { this.page.set(0); this.loadList(); }, 400); }
  onKind(e: Event) { this.kindF.set((e.target as HTMLSelectElement).value as '' | FeatureFlagKind); this.page.set(0); this.loadList(); }
  onStatus(e: Event) { this.statusF.set((e.target as HTMLSelectElement).value as '' | FlagStatus); this.page.set(0); this.loadList(); }
  onTargeting(e: Event) { this.targetingF.set((e.target as HTMLSelectElement).value as '' | FlagAudience); this.page.set(0); this.loadList(); }
  onHealth(e: Event) { this.healthF.set((e.target as HTMLSelectElement).value as '' | 'stale'); this.page.set(0); this.loadList(); }
  toggleSort(key: 'updated' | 'rollout' | 'status') { this.sortKey.set(key); this.page.set(0); this.loadList(); }
  clearFilters() { this.search.set(''); this.kindF.set(''); this.statusF.set(''); this.targetingF.set(''); this.healthF.set(''); this.page.set(0); this.loadList(); }
  goToPage(p: number) { if (p < 0 || p >= this.totalPages()) return; this.page.set(p); this.loadList(); }
  inspectKilled() { this.statusF.set('KILLED'); this.healthF.set(''); this.page.set(0); this.loadList(); }
  inspectStale() { this.healthF.set('stale'); this.statusF.set(''); this.page.set(0); this.loadList(); }

  // ── Display helpers ──────────────────────────────────────────────────────────
  kindTone(k: FeatureFlagKind): string { return k.toLowerCase(); }
  kindIcon(k: FeatureFlagKind): string { return k === 'OPS' ? 'bolt' : k === 'EXPERIMENT' ? 'beaker' : k === 'PERMISSION' ? 'key' : 'workflow'; }
  kindLabel(k: FeatureFlagKind): string { return this.i18n.t('ff_kind_' + k.toLowerCase()); }
  statusTone(s: FlagStatus): string { return s.toLowerCase(); }
  statusDot(s: FlagStatus): string { return s.toLowerCase(); }
  statusLabel(s: FlagStatus): string { return this.i18n.t('ff_status_' + s.toLowerCase()); }
  audienceLabel(a: FlagAudience): string { return this.i18n.t('ff_aud_' + a.toLowerCase()); }
  targetingSummary(f: FeatureFlag): string {
    if (f.audience === 'EVERYONE') return this.i18n.t('ff_aud_everyone');
    if (f.audience === 'STAFF') return this.i18n.t('ff_aud_staff');
    if (f.audience === 'ORG') return f.audienceOrgName ?? this.i18n.t('ff_aud_org');
    if (f.audience === 'PLAN') return f.audiencePlans.length ? f.audiencePlans.join(' + ') : this.i18n.t('ff_aud_plan');
    return '—';
  }
  rolloutLabel(f: FeatureFlag): string {
    if (f.status === 'OFF' || f.status === 'KILLED' || f.status === 'ARCHIVED') return this.i18n.t('ff_rollout_off');
    if (f.audience !== 'EVERYONE') return this.i18n.t('ff_targeted');
    return f.rollout + '%';
  }
  relative(at: string): string {
    const days = Math.floor((Date.now() - new Date(at).getTime()) / 86400000);
    if (days <= 0) return this.i18n.t('ff_ago_today'); if (days === 1) return this.i18n.t('ff_ago_yesterday');
    if (days < 30) return this.i18n.t('ff_ago_days', { n: days + '' });
    if (days < 365) return this.i18n.t('ff_ago_months', { n: Math.round(days / 30) + '' });
    return this.i18n.t('ff_ago_years', { n: (days / 365).toFixed(1) });
  }
  draftStatus(): FlagStatus {
    const d = this.draft(); const e = this.editing();
    if (e?.archived) return 'ARCHIVED'; if (e?.killed) return 'KILLED';
    if (!d.enabled || d.rollout === 0) return 'OFF';
    if (d.rollout === 100 && d.audience === 'EVERYONE') return 'ON';
    return 'ROLLING';
  }

  // ── Editor open/close ─────────────────────────────────────────────────────────
  openNew() { if (!this.canManage()) return; this.editing.set(null); this.creating.set(true); this.draft.set({ ...BLANK }); this.selectedOrgName.set(null); this.editorTab.set('rollout'); this.history.set([]); this.detailFlash.set(''); }
  openEdit(f: FeatureFlag) {
    this.creating.set(false); this.editing.set(f); this.editorTab.set('rollout'); this.detailFlash.set('');
    this.draft.set({ key: f.key, name: f.name, description: f.description ?? '', kind: f.kind, enabled: f.enabled, rollout: f.rollout,
      audience: f.audience, audiencePlans: [...f.audiencePlans], audienceOrgId: f.audienceOrgId, overrides: f.overrides.map(o => ({ ...o })) });
    this.selectedOrgName.set(f.audienceOrgName);
    this.history.set([]);
    this.service.get(f.id).subscribe({ next: d => this.history.set(d.history), error: () => {} });
  }
  closeEditor() { this.creating.set(false); this.editing.set(null); this.orgPickerOpen.set(false); }
  setEditorTab(t: EditorTab) { this.editorTab.set(t); }

  // ── Draft mutators ──────────────────────────────────────────────────────────
  patch<K extends keyof FlagDraft>(key: K, value: FlagDraft[K]) { this.draft.update(d => ({ ...d, [key]: value })); }
  patchInput<K extends keyof FlagDraft>(key: K, e: Event) { this.patch(key, (e.target as HTMLInputElement | HTMLTextAreaElement).value as FlagDraft[K]); }
  setKind(k: FeatureFlagKind) { this.patch('kind', k); }
  setAudience(a: FlagAudience) { this.patch('audience', a); if (a !== 'ORG') { this.patch('audienceOrgId', null); this.selectedOrgName.set(null); } if (a !== 'PLAN') this.patch('audiencePlans', []); }
  togglePlan(p: string) { this.draft.update(d => ({ ...d, audiencePlans: d.audiencePlans.includes(p) ? d.audiencePlans.filter(x => x !== p) : [...d.audiencePlans, p] })); }
  hasPlan(p: string): boolean { return this.draft().audiencePlans.includes(p); }
  toggleEnabled() { this.draft.update(d => ({ ...d, enabled: !d.enabled, rollout: !d.enabled && d.rollout === 0 ? 100 : d.rollout })); }
  onRollout(e: Event) { const v = Number((e.target as HTMLInputElement).value); this.patch('rollout', v); if (v > 0 && !this.draft().enabled) this.patch('enabled', true); }
  addOverride() { this.draft.update(d => ({ ...d, overrides: [...d.overrides, { scope: '', value: 'on' }] })); }
  removeOverride(i: number) { this.draft.update(d => ({ ...d, overrides: d.overrides.filter((_, idx) => idx !== i) })); }
  setOverrideScope(i: number, e: Event) { const v = (e.target as HTMLInputElement).value; this.draft.update(d => ({ ...d, overrides: d.overrides.map((o, idx) => idx === i ? { ...o, scope: v } : o) })); }
  setOverrideValue(i: number, value: 'on' | 'off') { this.draft.update(d => ({ ...d, overrides: d.overrides.map((o, idx) => idx === i ? { ...o, value } : o) })); }

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

  // ── Actions ───────────────────────────────────────────────────────────────────
  private flashMsg(m: string) { this.flash.set(m); if (this.flashTimer) this.timers.clear(this.flashTimer); this.flashTimer = this.timers.set(() => this.flash.set(''), 3200); }
  private detailFlashMsg(m: string) { this.detailFlash.set(m); if (this.detailFlashTimer) this.timers.clear(this.detailFlashTimer); this.detailFlashTimer = this.timers.set(() => this.detailFlash.set(''), 2600); }
  private applyRow(f: FeatureFlag) { this.rows.update(list => { const i = list.findIndex(x => x.id === f.id); return i >= 0 ? list.map(x => x.id === f.id ? f : x) : [f, ...list]; }); this.loadKpis(); }
  private updateBody() { const d = this.draft(); return { name: d.name, description: d.description, kind: d.kind, enabled: d.enabled, rollout: d.rollout, audience: d.audience, audiencePlans: d.audiencePlans, audienceOrgId: d.audienceOrgId, overrides: d.overrides.filter(o => o.scope.trim()) }; }

  save() {
    if (!this.canManage() || this.busy()) return;
    const d = this.draft(); const e = this.editing();
    this.busy.set(true);
    if (e) {
      this.service.update(e.id, this.updateBody()).subscribe({
        next: f => { this.busy.set(false); this.editing.set(f); this.applyRow(f); this.detailFlashMsg(this.i18n.t('ff_flash_saved')); this.flashMsg(this.i18n.t('ff_flash_saved')); },
        error: () => { this.busy.set(false); this.error.set(this.i18n.t('ff_action_failed')); },
      });
    } else {
      this.service.create({ key: d.key.trim(), name: d.name.trim(), description: d.description, kind: d.kind }).subscribe({
        next: created => {
          // apply full draft (rollout / targeting / overrides) via update
          this.service.update(created.id, this.updateBody()).subscribe({
            next: f => { this.busy.set(false); this.editing.set(f); this.creating.set(false); this.applyRow(f); this.detailFlashMsg(this.i18n.t('ff_flash_created')); this.flashMsg(this.i18n.t('ff_flash_created')); },
            error: () => { this.busy.set(false); this.editing.set(created); this.creating.set(false); this.applyRow(created); },
          });
        },
        error: () => { this.busy.set(false); this.error.set(this.i18n.t('ff_create_failed')); },
      });
    }
  }

  private op(obs: ReturnType<AdminFeatureFlagService['enable']>, msg: string) {
    this.busy.set(true);
    obs.subscribe({
      next: f => { this.busy.set(false); this.editing.set(f); this.applyRow(f); this.patch('enabled', f.enabled); this.patch('rollout', f.rollout); this.detailFlashMsg(msg); this.flashMsg(msg); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('ff_action_failed')); },
    });
  }
  enable() { const e = this.editing(); if (!e || !this.canManage() || this.busy()) return; this.op(this.service.enable(e.id), this.i18n.t('ff_flash_enabled')); }
  disable() { const e = this.editing(); if (!e || !this.canManage() || this.busy()) return; this.op(this.service.disable(e.id), this.i18n.t('ff_flash_disabled')); }
  restore() { const e = this.editing(); if (!e || !this.canManage() || this.busy()) return; this.op(this.service.restore(e.id), this.i18n.t('ff_flash_restored')); }
  async kill() {
    const e = this.editing(); if (!e || !this.canManage()) return;
    const ok = await this.confirmDialog.confirm({ title: this.i18n.t('ff_kill_title', { k: e.key }), message: this.i18n.t('ff_kill_msg'), confirmText: this.i18n.t('ff_kill'), cancelText: this.i18n.t('confirm_cancel'), tone: 'danger' });
    if (!ok) return; this.op(this.service.kill(e.id), this.i18n.t('ff_flash_killed'));
  }
  async archive() {
    const e = this.editing(); if (!e || !this.canManage()) return;
    const ok = await this.confirmDialog.confirm({ title: this.i18n.t('ff_archive_title', { k: e.key }), message: this.i18n.t('ff_archive_msg'), confirmText: this.i18n.t('ff_archive'), cancelText: this.i18n.t('confirm_cancel'), tone: 'danger' });
    if (!ok) return;
    this.busy.set(true);
    this.service.archive(e.id).subscribe({ next: f => { this.busy.set(false); this.editing.set(f); this.applyRow(f); this.detailFlashMsg(this.i18n.t('ff_flash_archived')); }, error: () => { this.busy.set(false); this.error.set(this.i18n.t('ff_action_failed')); } });
  }
  duplicate() {
    const e = this.editing(); if (!e || !this.canManage() || this.busy()) return;
    this.busy.set(true);
    this.service.duplicate(e.id).subscribe({ next: f => { this.busy.set(false); this.applyRow(f); this.flashMsg(this.i18n.t('ff_flash_duplicated')); this.closeEditor(); }, error: () => { this.busy.set(false); this.error.set(this.i18n.t('ff_action_failed')); } });
  }

  // row-menu quick actions
  rowToggle(f: FeatureFlag, e: Event) { e.stopPropagation(); this.openMenuId.set(null); if (!this.canManage() || this.busy()) return; this.busy.set(true); const obs = f.enabled ? this.service.disable(f.id) : this.service.enable(f.id); obs.subscribe({ next: u => { this.busy.set(false); this.applyRow(u); this.flashMsg(this.i18n.t(u.enabled ? 'ff_flash_enabled' : 'ff_flash_disabled')); }, error: () => { this.busy.set(false); this.error.set(this.i18n.t('ff_action_failed')); } }); }
  rowKill(f: FeatureFlag, e: Event) { e.stopPropagation(); this.openMenuId.set(null); this.openEdit(f); this.timers.set(() => this.kill(), 0); }
  rowArchive(f: FeatureFlag, e: Event) { e.stopPropagation(); this.openMenuId.set(null); this.openEdit(f); this.timers.set(() => this.archive(), 0); }
  rowDuplicate(f: FeatureFlag, e: Event) { e.stopPropagation(); this.openMenuId.set(null); this.openEdit(f); this.timers.set(() => this.duplicate(), 0); }
  rowRestore(f: FeatureFlag, e: Event) { e.stopPropagation(); this.openMenuId.set(null); if (!this.canManage() || this.busy()) return; this.busy.set(true); this.service.restore(f.id).subscribe({ next: u => { this.busy.set(false); this.applyRow(u); this.flashMsg(this.i18n.t('ff_flash_restored')); }, error: () => { this.busy.set(false); this.error.set(this.i18n.t('ff_action_failed')); } }); }

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
