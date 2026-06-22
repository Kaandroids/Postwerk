import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminStaffService } from '../../../../core/services/admin-staff.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { managedTimers } from '../../../../shared/utils/managed-timers';
import { PageContentComponent } from '../page-content/page-content.component';
import {
  StaffMember,
  StaffKpis,
  StaffRolesMatrix,
  StaffCandidate,
  StaffRoleKey,
  StaffTier,
  StaffFilters,
} from '../../../../models/admin-staff.model';

type View = 'staff' | 'roles';
type DetailTab = 'caps' | 'activity';

/** Capability vocabulary grouped by area for the matrix (label keys → i18n). */
const CAP_GROUPS: { area: string; caps: { id: string; key: string }[] }[] = [
  { area: 'sf_area_platform', caps: [{ id: 'PLATFORM_DASHBOARD_VIEW', key: 'sf_cap_platform_dashboard_view' }] },
  { area: 'sf_area_users', caps: [
    { id: 'USER_VIEW', key: 'sf_cap_user_view' }, { id: 'USER_MANAGE', key: 'sf_cap_user_manage' }, { id: 'USER_CREDENTIAL_RESET', key: 'sf_cap_user_credential_reset' }] },
  { area: 'sf_area_orgs', caps: [{ id: 'ORG_VIEW', key: 'sf_cap_org_view' }, { id: 'ORG_MANAGE', key: 'sf_cap_org_manage' }] },
  { area: 'sf_area_billing', caps: [
    { id: 'PLAN_VIEW', key: 'sf_cap_plan_view' }, { id: 'PLAN_MANAGE', key: 'sf_cap_plan_manage' }, { id: 'BILLING_VIEW', key: 'sf_cap_billing_view' }, { id: 'BILLING_MANAGE', key: 'sf_cap_billing_manage' }, { id: 'QUOTA_OVERRIDE', key: 'sf_cap_quota_override' }] },
  { area: 'sf_area_ai', caps: [
    { id: 'AI_USAGE_VIEW', key: 'sf_cap_ai_usage_view' }, { id: 'AUTOMATION_OVERSIGHT_VIEW', key: 'sf_cap_automation_oversight_view' }, { id: 'PROMPT_MANAGE', key: 'sf_cap_prompt_manage' }] },
  { area: 'sf_area_infra', caps: [{ id: 'INFRA_VIEW', key: 'sf_cap_infra_view' }, { id: 'INFRA_MANAGE', key: 'sf_cap_infra_manage' }] },
  { area: 'sf_area_marketplace', caps: [{ id: 'MARKETPLACE_MODERATE', key: 'sf_cap_marketplace_moderate' }] },
  { area: 'sf_area_compliance', caps: [{ id: 'COMPLIANCE_VIEW', key: 'sf_cap_compliance_view' }, { id: 'COMPLIANCE_MANAGE', key: 'sf_cap_compliance_manage' }] },
  { area: 'sf_area_system', caps: [
    { id: 'AUDIT_LOG_VIEW', key: 'sf_cap_audit_log_view' }, { id: 'FEATURE_FLAG_MANAGE', key: 'sf_cap_feature_flag_manage' }, { id: 'ANNOUNCEMENT_MANAGE', key: 'sf_cap_announcement_manage' }, { id: 'STAFF_MANAGE', key: 'sf_cap_staff_manage' }] },
];

/**
 * Platform-staff Staff & Roles console: the staff roster + the read-only role→capability matrix,
 * with grant / change-role / revoke. Gated STAFF_MANAGE (Super Admin only); self-change is blocked.
 */
@Component({
  selector: 'app-admin-staff',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-staff.component.html',
  styleUrl: './admin-staff.component.scss',
})
export class AdminStaffComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private service = inject(AdminStaffService);
  private confirmDialog = inject(ConfirmDialogService);
  private timers = managedTimers();

  readonly capGroups = CAP_GROUPS;
  readonly roleKeys: StaffRoleKey[] = ['SUPER_ADMIN', 'ADMIN', 'BILLING', 'MODERATOR', 'SUPPORT', 'AUDITOR'];

  view = signal<View>('staff');

  // ── List state ──────────────────────────────────────────────────────────────
  rows = signal<StaffMember[]>([]);
  total = signal(0);
  totalPages = signal(0);
  page = signal(0);
  loading = signal(true);
  error = signal('');
  kpis = signal<StaffKpis | null>(null);
  matrix = signal<StaffRolesMatrix | null>(null);
  refreshing = signal(false);
  updatedAt = signal('');
  alertDismissed = signal(false);
  busy = signal(false);
  flash = signal('');
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  // filters
  search = signal('');
  roleF = signal<'' | StaffRoleKey>('');
  tierF = signal<'' | StaffTier>('');
  sortKey = signal<'role' | 'lastActive' | 'staffSince'>('role');

  // detail modal
  editing = signal<StaffMember | null>(null);
  picked = signal<StaffRoleKey>('SUPPORT');
  detailTab = signal<DetailTab>('caps');
  detailFlash = signal('');
  private detailFlashTimer: ReturnType<typeof setTimeout> | null = null;

  // grant modal
  granting = signal(false);
  grantSearch = signal('');
  grantResults = signal<StaffCandidate[]>([]);
  grantSelected = signal<StaffCandidate | null>(null);
  grantRole = signal<StaffRoleKey>('SUPPORT');

  readonly pageSize = 10;
  readonly canManage = computed(() => this.identity.has('STAFF_MANAGE'));
  readonly hasFilters = computed(() => !!this.search() || !!this.roleF() || !!this.tierF());
  readonly soleSuperAdmin = computed(() => (this.kpis()?.superAdmins ?? 0) === 1 && !this.alertDismissed());

  ngOnInit() {
    this.loadKpis();
    this.loadMatrix();
    this.stampUpdated();
    this.loadRoster();
  }

  private filters(): StaffFilters {
    return { search: this.search() || undefined, role: this.roleF() || undefined, tier: this.tierF() || undefined, sort: this.sortKey() };
  }
  loadRoster() {
    this.loading.set(true); this.error.set('');
    this.service.roster(this.filters(), this.page(), this.pageSize).subscribe({
      next: p => { this.rows.set(p.content); this.total.set(p.totalElements); this.totalPages.set(p.totalPages); this.loading.set(false); },
      error: () => { this.rows.set([]); this.loading.set(false); this.error.set(this.i18n.t('sf_load_failed')); },
    });
  }
  loadKpis() { this.service.kpis().subscribe({ next: k => this.kpis.set(k), error: () => {} }); }
  loadMatrix() { this.service.roles().subscribe({ next: m => this.matrix.set(m), error: () => {} }); }
  private stampUpdated() { const d = new Date(); const p = (n: number) => String(n).padStart(2, '0'); this.updatedAt.set(`${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`); }
  refresh() { if (this.refreshing()) return; this.refreshing.set(true); this.loadKpis(); this.loadMatrix(); this.loadRoster(); this.stampUpdated(); this.timers.set(() => this.refreshing.set(false), 600); }
  setView(v: View) { this.view.set(v); }

  // ── Filters ──────────────────────────────────────────────────────────────────
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  onSearch(e: Event) { this.search.set((e.target as HTMLInputElement).value); if (this.searchTimer) this.timers.clear(this.searchTimer); this.searchTimer = this.timers.set(() => { this.page.set(0); this.loadRoster(); }, 400); }
  onRole(e: Event) { this.roleF.set((e.target as HTMLSelectElement).value as '' | StaffRoleKey); this.page.set(0); this.loadRoster(); }
  onTier(e: Event) { this.tierF.set((e.target as HTMLSelectElement).value as '' | StaffTier); this.page.set(0); this.loadRoster(); }
  toggleSort(key: 'role' | 'lastActive' | 'staffSince') { this.sortKey.set(key); this.page.set(0); this.loadRoster(); }
  clearFilters() { this.search.set(''); this.roleF.set(''); this.tierF.set(''); this.page.set(0); this.loadRoster(); }
  goToPage(p: number) { if (p < 0 || p >= this.totalPages()) return; this.page.set(p); this.loadRoster(); }
  inspectBanner() { this.view.set('staff'); this.roleF.set('SUPER_ADMIN'); this.tierF.set(''); this.page.set(0); this.loadRoster(); }

  // ── Display helpers ──────────────────────────────────────────────────────────
  initial(name: string): string { return (name?.trim()?.[0] ?? '?').toUpperCase(); }
  avatarHue(seed: string): number { const s = seed ?? ''; let h = 0; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360; return h; }
  roleLabel(r: StaffRoleKey | null): string { return r ? this.i18n.t('sf_role_' + r.toLowerCase()) : '—'; }
  roleIcon(r: StaffRoleKey): string {
    return r === 'SUPER_ADMIN' ? 'key' : r === 'ADMIN' ? 'shield' : r === 'BILLING' ? 'card' : r === 'MODERATOR' ? 'market' : r === 'SUPPORT' ? 'help' : 'search';
  }
  rolePurpose(r: StaffRoleKey): string { return this.i18n.t('sf_purpose_' + r.toLowerCase()); }
  tierLabel(t: StaffTier | null): string { return t ? this.i18n.t(t === 'PRIVILEGED' ? 'sf_tier_privileged' : 'sf_tier_readonly') : '—'; }
  capLabel(key: string): string { return this.i18n.t(key); }
  areaLabel(key: string): string { return this.i18n.t(key); }
  capsFor(role: StaffRoleKey): string[] { return this.matrix()?.roles.find(r => r.key === role)?.permissions ?? []; }
  capCount(role: StaffRoleKey): number { return this.capsFor(role).length; }
  topAreas(role: StaffRoleKey): string {
    const caps = this.capsFor(role);
    const areas = this.capGroups.filter(g => g.area !== 'sf_area_platform' && g.caps.some(c => caps.includes(c.id))).map(g => this.areaLabel(g.area));
    return areas.slice(0, 3).join(' · ');
  }
  relative(at: string | null): string {
    if (!at) return '—';
    const days = Math.floor((Date.now() - new Date(at).getTime()) / 86400000);
    if (days <= 0) return this.i18n.t('sf_active_today'); if (days === 1) return this.i18n.t('sf_ago_yesterday');
    if (days < 30) return this.i18n.t('sf_ago_days', { n: days + '' });
    if (days < 365) return this.i18n.t('sf_ago_months', { n: Math.round(days / 30) + '' });
    return this.i18n.t('sf_ago_years', { n: (days / 365).toFixed(1) });
  }
  pickedChanged(): boolean { const e = this.editing(); return !!e && this.picked() !== e.role; }
  granted(capId: string): boolean { return this.capsFor(this.picked()).includes(capId); }

  // ── Detail modal ────────────────────────────────────────────────────────────
  openMember(m: StaffMember) { this.editing.set(m); this.picked.set(m.role); this.detailTab.set('caps'); this.detailFlash.set(''); }
  closeDetail() { this.editing.set(null); }
  setPicked(r: StaffRoleKey) { this.picked.set(r); }
  setDetailTab(t: DetailTab) { this.detailTab.set(t); }

  @HostListener('document:keydown.escape')
  onEscape() { if (this.granting()) { this.granting.set(false); return; } if (this.editing()) this.closeDetail(); }
  @HostListener('document:click')
  onDocClick() { this.openMenuId.set(null); }
  @HostListener('window:scroll')
  @HostListener('window:resize')
  onViewport() { this.openMenuId.set(null); }

  // ── Actions ───────────────────────────────────────────────────────────────────
  private flashMsg(m: string) { this.flash.set(m); if (this.flashTimer) this.timers.clear(this.flashTimer); this.flashTimer = this.timers.set(() => this.flash.set(''), 3200); }
  private detailFlashMsg(m: string) { this.detailFlash.set(m); if (this.detailFlashTimer) this.timers.clear(this.detailFlashTimer); this.detailFlashTimer = this.timers.set(() => this.detailFlash.set(''), 2600); }
  private applyRow(m: StaffMember) { this.rows.update(list => list.map(x => x.id === m.id ? m : x)); this.loadKpis(); }

  saveRole() {
    const e = this.editing(); if (!e || e.self || !this.canManage() || this.busy() || !this.pickedChanged()) return;
    this.busy.set(true);
    this.service.setRole(e.id, this.picked()).subscribe({
      next: m => { this.busy.set(false); this.editing.set(m); this.applyRow(m); this.detailFlashMsg(this.i18n.t('sf_flash_role', { role: this.roleLabel(m.role) })); this.flashMsg(this.i18n.t('sf_flash_role', { role: this.roleLabel(m.role) })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('sf_action_failed')); },
    });
  }
  async revokeMember() {
    const e = this.editing(); if (!e || e.self || !this.canManage()) return;
    const ok = await this.confirmDialog.confirm({ title: this.i18n.t('sf_revoke_title', { name: e.name }), message: this.i18n.t('sf_revoke_msg'), confirmText: this.i18n.t('sf_revoke'), cancelText: this.i18n.t('confirm_cancel'), tone: 'danger' });
    if (!ok) return;
    this.busy.set(true);
    this.service.revoke(e.id).subscribe({
      next: () => { this.busy.set(false); this.rows.update(list => list.filter(x => x.id !== e.id)); this.loadKpis(); this.closeDetail(); this.flashMsg(this.i18n.t('sf_flash_revoked', { name: e.name })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('sf_action_failed')); },
    });
  }

  // row menu
  async rowRevoke(m: StaffMember, e: Event) {
    e.stopPropagation(); this.openMenuId.set(null);
    if (m.self || !this.canManage()) return;
    const ok = await this.confirmDialog.confirm({ title: this.i18n.t('sf_revoke_title', { name: m.name }), message: this.i18n.t('sf_revoke_msg'), confirmText: this.i18n.t('sf_revoke'), cancelText: this.i18n.t('confirm_cancel'), tone: 'danger' });
    if (!ok) return;
    this.busy.set(true);
    this.service.revoke(m.id).subscribe({ next: () => { this.busy.set(false); this.rows.update(list => list.filter(x => x.id !== m.id)); this.loadKpis(); this.flashMsg(this.i18n.t('sf_flash_revoked', { name: m.name })); }, error: () => { this.busy.set(false); this.error.set(this.i18n.t('sf_action_failed')); } });
  }

  // ── Grant modal ─────────────────────────────────────────────────────────────
  openGrant() { if (!this.canManage()) return; this.grantSearch.set(''); this.grantResults.set([]); this.grantSelected.set(null); this.grantRole.set('SUPPORT'); this.granting.set(true); }
  closeGrant() { this.granting.set(false); }
  private grantTimer: ReturnType<typeof setTimeout> | null = null;
  onGrantSearch(e: Event) {
    const q = (e.target as HTMLInputElement).value; this.grantSearch.set(q); this.grantSelected.set(null);
    if (this.grantTimer) this.timers.clear(this.grantTimer);
    this.grantTimer = this.timers.set(() => this.service.candidates(q).subscribe({ next: r => this.grantResults.set(r), error: () => this.grantResults.set([]) }), 300);
  }
  selectCandidate(c: StaffCandidate) { this.grantSelected.set(c); this.grantResults.set([]); this.grantSearch.set(c.name + ' · ' + c.email); }
  setGrantRole(r: StaffRoleKey) { this.grantRole.set(r); }
  submitGrant() {
    const c = this.grantSelected(); if (!c || !this.canManage() || this.busy()) return;
    this.busy.set(true);
    this.service.setRole(c.id, this.grantRole()).subscribe({
      next: () => { this.busy.set(false); this.granting.set(false); this.page.set(0); this.loadRoster(); this.loadKpis(); this.flashMsg(this.i18n.t('sf_flash_granted', { name: c.name })); },
      error: () => { this.busy.set(false); this.error.set(this.i18n.t('sf_action_failed')); },
    });
  }

  // ── Row menu ──────────────────────────────────────────────────────────────────
  openMenuId = signal<string | null>(null);
  menuPos = signal<{ top: number; left: number } | null>(null);
  toggleMenu(id: string, event: Event) {
    event.stopPropagation();
    if (this.openMenuId() === id) { this.openMenuId.set(null); return; }
    const r = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.menuPos.set({ top: r.bottom + 4, left: Math.max(8, r.right - 196) });
    this.openMenuId.set(id);
  }
}
