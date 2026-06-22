import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, OnInit, HostListener } from '@angular/core';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { FocusTrapDirective } from '../../../../shared/directives/focus-trap.directive';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { GrantCreditDialogComponent } from '../../../../shared/components/grant-credit-dialog/grant-credit-dialog.component';
import { PageContentComponent } from '../page-content/page-content.component';
import { AdminUser, PlanModel, AdminUserOrg, AdminMailbox, AdminAuditLog, StaffNote } from '../../../../models/admin.model';
import { humanizeError } from '../../../../shared/utils/error.util';
import { planTone } from '../../../../shared/utils/admin-format.util';
import { managedTimers } from '../../../../shared/utils/managed-timers';

type SortKey = 'fullName' | 'createdAt';
type SortDir = 'asc' | 'desc';
type DetailTab = 'overview' | 'plan' | 'staff' | 'orgs' | 'mailboxes' | 'security' | 'notes' | 'activity' | 'gdpr';

interface DetailTabDef {
  key: DetailTab;
  labelKey: string;
}

const STAFF_ROLES = ['SUPER_ADMIN', 'ADMIN', 'SUPPORT', 'BILLING', 'MODERATOR', 'AUDITOR'];

/** Admin page listing all platform users with role management, staff access, plan assignment, and a centered detail modal. */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ErrorBannerComponent, GrantCreditDialogComponent, PageContentComponent, FocusTrapDirective],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.scss',
})
export class AdminUsersComponent implements OnInit {
  protected i18n = inject(I18nService);
  protected fmt = inject(FormatService);
  protected identity = inject(AdminIdentityService);
  private adminService = inject(AdminService);
  private confirmDialog = inject(ConfirmDialogService);
  private timers = managedTimers();

  readonly staffRoles = STAFF_ROLES;

  // List state
  users = signal<AdminUser[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  currentPage = signal(0);
  pageSize = signal(20);
  loading = signal(true);

  search = signal('');
  roleFilter = signal<string>('');
  statusFilter = signal<string>('');
  planFilter = signal<string>('');
  sortKey = signal<SortKey>('fullName');
  sortDir = signal<SortDir>('asc');

  // Detail modal state
  selectedUser = signal<AdminUser | null>(null);
  activeTab = signal<DetailTab>('overview');
  plans = signal<PlanModel[]>([]);
  copiedId = signal(false);
  busy = signal(false);

  error = signal('');

  // ── Lazy-loaded detail-tab data (fetched on first open of each tab per user) ──
  orgsLoading = signal(false);
  orgsData = signal<AdminUserOrg[] | null>(null);
  orgsError = signal(false);

  mailboxesLoading = signal(false);
  mailboxesData = signal<AdminMailbox[] | null>(null);
  mailboxesError = signal(false);

  activityLoading = signal(false);
  activityData = signal<AdminAuditLog[] | null>(null);
  activityError = signal(false);

  // ── Sessions & Security tab ──
  sessionsLoading = signal(false);
  sessionsCount = signal<number | null>(null);
  sessionsError = signal(false);
  revoking = signal(false);

  // ── Staff Notes tab ──
  notesLoading = signal(false);
  notesData = signal<StaffNote[] | null>(null);
  notesError = signal(false);
  noteDraft = signal('');
  noteSubmitting = signal(false);
  deletingNoteId = signal<string | null>(null);

  // ── Grant AI credit shortcut ──
  grantCreditOpen = signal(false);

  // Transient success flash shown in the modal action area / tabs.
  flash = signal('');
  private flashTimeout: ReturnType<typeof setTimeout> | null = null;

  readonly detailTabs: DetailTabDef[] = [
    { key: 'overview', labelKey: 'admin_user_tab_overview' },
    { key: 'plan', labelKey: 'admin_user_tab_plan' },
    { key: 'staff', labelKey: 'admin_user_tab_staff' },
    { key: 'orgs', labelKey: 'admin_user_tab_orgs' },
    { key: 'mailboxes', labelKey: 'admin_user_tab_mailboxes' },
    { key: 'security', labelKey: 'admin_user_tab_security' },
    { key: 'notes', labelKey: 'admin_user_tab_notes' },
    { key: 'activity', labelKey: 'admin_user_tab_activity' },
    { key: 'gdpr', labelKey: 'admin_user_tab_gdpr' },
  ];

  // Sorted rows (client-side sort over the current page; backend handles paging/filtering)
  readonly sortedUsers = computed(() => {
    const list = [...this.users()];
    const key = this.sortKey();
    const dir = this.sortDir() === 'asc' ? 1 : -1;
    list.sort((a, b) => {
      const va = (a[key] ?? '') as string;
      const vb = (b[key] ?? '') as string;
      return va.localeCompare(vb) * dir;
    });
    return list;
  });

  /** Plan names for the Plan filter dropdown, sourced from the real plans list. */
  readonly planNames = computed(() => this.plans().map(p => p.name));

  readonly rangeStart = computed(() =>
    this.totalElements() === 0 ? 0 : this.currentPage() * this.pageSize() + 1
  );
  readonly rangeEnd = computed(() =>
    Math.min((this.currentPage() + 1) * this.pageSize(), this.totalElements())
  );

  constructor() {
    // Lazy-load each wired tab's data when first opened for the selected user.
    effect(() => {
      const user = this.selectedUser();
      const tab = this.activeTab();
      if (!user) return;
      switch (tab) {
        case 'orgs': this.ensureOrgs(user.id); break;
        case 'mailboxes': this.ensureMailboxes(user.id); break;
        case 'activity': this.ensureActivity(user.id); break;
        case 'security': this.ensureSessions(user.id); break;
        case 'notes': this.ensureNotes(user.id); break;
      }
    });
  }

  ngOnInit() {
    this.loadUsers();
    this.adminService.getPlans().subscribe({
      next: p => this.plans.set(p),
      error: () => { /* plans are optional UI sugar; ignore */ },
    });
  }

  // ── Lazy tab loaders (idempotent: skip if already loaded/loading) ────
  private ensureOrgs(userId: string) {
    if (this.orgsData() !== null || this.orgsLoading()) return;
    this.orgsLoading.set(true);
    this.orgsError.set(false);
    this.adminService.getUserOrganizations(userId).subscribe({
      next: rows => { this.orgsData.set(rows); this.orgsLoading.set(false); },
      error: () => { this.orgsError.set(true); this.orgsLoading.set(false); },
    });
  }

  private ensureMailboxes(userId: string) {
    if (this.mailboxesData() !== null || this.mailboxesLoading()) return;
    this.mailboxesLoading.set(true);
    this.mailboxesError.set(false);
    this.adminService.getUserMailboxes(userId).subscribe({
      next: rows => { this.mailboxesData.set(rows); this.mailboxesLoading.set(false); },
      error: () => { this.mailboxesError.set(true); this.mailboxesLoading.set(false); },
    });
  }

  private ensureActivity(userId: string) {
    if (this.activityData() !== null || this.activityLoading()) return;
    this.activityLoading.set(true);
    this.activityError.set(false);
    this.adminService.getAuditLog(userId, undefined, 0, 50).subscribe({
      next: page => { this.activityData.set(page.content); this.activityLoading.set(false); },
      error: () => { this.activityError.set(true); this.activityLoading.set(false); },
    });
  }

  private ensureSessions(userId: string) {
    if (this.sessionsCount() !== null || this.sessionsLoading()) return;
    this.sessionsLoading.set(true);
    this.sessionsError.set(false);
    this.adminService.getUserSessions(userId).subscribe({
      next: s => { this.sessionsCount.set(s.activeSessions); this.sessionsLoading.set(false); },
      error: () => { this.sessionsError.set(true); this.sessionsLoading.set(false); },
    });
  }

  private ensureNotes(userId: string) {
    if (this.notesData() !== null || this.notesLoading()) return;
    this.notesLoading.set(true);
    this.notesError.set(false);
    this.adminService.getUserNotes(userId).subscribe({
      next: rows => { this.notesData.set(rows); this.notesLoading.set(false); },
      error: () => { this.notesError.set(true); this.notesLoading.set(false); },
    });
  }

  /** Clears all lazy-loaded tab data so a freshly opened user re-fetches. */
  private resetTabData() {
    this.orgsData.set(null); this.orgsLoading.set(false); this.orgsError.set(false);
    this.mailboxesData.set(null); this.mailboxesLoading.set(false); this.mailboxesError.set(false);
    this.activityData.set(null); this.activityLoading.set(false); this.activityError.set(false);
    this.sessionsCount.set(null); this.sessionsLoading.set(false); this.sessionsError.set(false); this.revoking.set(false);
    this.notesData.set(null); this.notesLoading.set(false); this.notesError.set(false);
    this.noteDraft.set(''); this.noteSubmitting.set(false); this.deletingNoteId.set(null);
  }

  /** Shows a transient success message in the modal, auto-clearing after a few seconds. */
  private showFlash(msg: string) {
    this.flash.set(msg);
    if (this.flashTimeout) this.timers.clear(this.flashTimeout);
    this.flashTimeout = this.timers.set(() => this.flash.set(''), 4000);
  }

  loadUsers() {
    this.loading.set(true);
    this.adminService
      .getUsers(
        this.search(),
        this.roleFilter() || undefined,
        this.statusFilter() || undefined,
        this.planFilter() || undefined,
        this.currentPage(),
        this.pageSize())
      .subscribe({
        next: page => {
          this.users.set(page.content);
          this.totalElements.set(page.totalElements);
          this.totalPages.set(page.totalPages);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  onSearch(event: Event) {
    this.search.set((event.target as HTMLInputElement).value);
    if (this.searchTimeout) this.timers.clear(this.searchTimeout);
    this.searchTimeout = this.timers.set(() => {
      this.currentPage.set(0);
      this.loadUsers();
    }, 400);
  }

  onRoleFilter(event: Event) {
    this.roleFilter.set((event.target as HTMLSelectElement).value);
    this.currentPage.set(0);
    this.loadUsers();
  }

  onStatusFilter(event: Event) {
    this.statusFilter.set((event.target as HTMLSelectElement).value);
    this.currentPage.set(0);
    this.loadUsers();
  }

  onPlanFilter(event: Event) {
    this.planFilter.set((event.target as HTMLSelectElement).value);
    this.currentPage.set(0);
    this.loadUsers();
  }

  toggleSort(key: SortKey) {
    if (this.sortKey() === key) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortKey.set(key);
      this.sortDir.set('asc');
    }
  }

  goToPage(page: number) {
    if (page < 0 || page >= this.totalPages()) return;
    this.currentPage.set(page);
    this.loadUsers();
  }

  // ── Avatar helpers ──────────────────────────────────────────────────
  initials(user: AdminUser): string {
    const name = (user.fullName || user.email || '').trim();
    if (!name) return '?';
    const parts = name.split(/\s+/).filter(Boolean);
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  /** Deterministic hue from a string so avatars are stable per user. */
  avatarHue(user: AdminUser): number {
    const s = user.id || user.email || '';
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
    return h;
  }

  // ── Plan + AI usage helpers (list cells) ────────────────────────────
  /** Canonical plan→badge-tone mapping, shared with the other admin pages. */
  protected planTone = planTone;

  /** AI cap state for a user: 'unlimited' (-1), 'off' (0), or 'capped' (>0). */
  aiCapState(user: AdminUser): 'unlimited' | 'off' | 'capped' {
    if (user.costLimitCents < 0) return 'unlimited';
    if (user.costLimitCents === 0) return 'off';
    return 'capped';
  }

  /** Used/cap ratio clamped to [0,1]. Cap in cents → micros via ×10_000 (1 cent = 10k micros). */
  aiUsageRatio(user: AdminUser): number {
    if (user.costLimitCents <= 0) return 0;
    const capMicros = user.costLimitCents * 10_000;
    return Math.min(Math.max(user.aiCostMicrosThisMonth / capMicros, 0), 1);
  }

  /** Mini-bar tone by ratio: green <70%, amber 70–90%, red >90%. */
  aiUsageTone(user: AdminUser): 'green' | 'amber' | 'danger' {
    const r = this.aiUsageRatio(user);
    if (r > 0.9) return 'danger';
    if (r > 0.7) return 'amber';
    return 'green';
  }

  /** Formats a micros amount as a EUR string, e.g. "€1.23". */
  eurFromMicros(micros: number): string {
    return '€' + (micros / 1_000_000).toFixed(2);
  }

  /** Formats a cents cap as a EUR string, e.g. "€5.00". */
  eurFromCents(cents: number): string {
    return '€' + (cents / 100).toFixed(2);
  }

  /** Badge tone for an org membership role. */
  roleTone(role: string): string {
    switch ((role ?? '').toUpperCase()) {
      case 'OWNER': return 'plum';
      case 'ADMIN': return 'accent';
      case 'MEMBER': return 'slate';
      default: return 'slate';
    }
  }

  /** Badge tone for a membership/account status. */
  statusTone(status: string): string {
    return (status ?? '').toUpperCase() === 'ACTIVE' ? 'green' : 'slate';
  }

  // ── Detail modal ────────────────────────────────────────────────────
  selectUser(user: AdminUser) {
    this.resetTabData();
    this.selectedUser.set(user);
    this.activeTab.set('overview');
    this.copiedId.set(false);
    this.error.set('');
    this.flash.set('');
  }

  closeDetail() {
    this.selectedUser.set(null);
    this.grantCreditOpen.set(false);
    this.flash.set('');
  }

  // ── Grant AI credit shortcut ────────────────────────────────────────
  openGrantCredit() {
    if (!this.identity.has('QUOTA_OVERRIDE')) return;
    this.grantCreditOpen.set(true);
  }

  onCreditGranted(e: { amountCents: number; targetName: string }) {
    this.grantCreditOpen.set(false);
    this.showFlash(this.i18n.t('gcd_flash', { amount: this.eurFromCents(e.amountCents), name: e.targetName }));
  }

  /** Permission required to open a detail tab, or null if the tab is ungated. */
  tabPermission(tab: DetailTab): string | null {
    if (tab === 'activity') return 'AUDIT_LOG_VIEW';
    if (tab === 'staff') return 'STAFF_MANAGE';
    return null;
  }

  /** Whether the given detail tab is locked for the current staff role. */
  tabLocked(tab: DetailTab): boolean {
    const perm = this.tabPermission(tab);
    return perm !== null && !this.identity.has(perm);
  }

  setTab(tab: DetailTab) {
    if (this.tabLocked(tab)) return;
    this.activeTab.set(tab);
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.grantCreditOpen()) { this.grantCreditOpen.set(false); return; }
    if (this.selectedUser()) this.closeDetail();
  }

  async copyUserId(id: string) {
    try {
      await navigator.clipboard.writeText(id);
      this.copiedId.set(true);
      this.timers.set(() => this.copiedId.set(false), 1600);
    } catch {
      /* clipboard unavailable — silent */
    }
  }

  // ── Actions (all real) ──────────────────────────────────────────────
  private applyUpdate(updated: AdminUser) {
    this.users.update(list => list.map(u => (u.id === updated.id ? updated : u)));
    if (this.selectedUser()?.id === updated.id) this.selectedUser.set(updated);
  }

  toggleRole(user: AdminUser) {
    const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN';
    this.error.set('');
    this.busy.set(true);
    this.adminService.updateRole(user.id, newRole).subscribe({
      next: updated => { this.applyUpdate(updated); this.busy.set(false); },
      error: err => { this.error.set(humanizeError(err, this.i18n.t('admin_users_action_failed'))); this.busy.set(false); },
    });
  }

  onStaffRoleChange(user: AdminUser, event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    const staffRole = value === '' ? null : value;
    if (staffRole === (user.staffRole ?? null)) return;
    this.error.set('');
    this.busy.set(true);
    this.adminService.updateStaffRole(user.id, staffRole).subscribe({
      next: updated => { this.applyUpdate(updated); this.busy.set(false); },
      error: err => { this.error.set(humanizeError(err, this.i18n.t('admin_users_action_failed'))); this.busy.set(false); },
    });
  }

  onPlanChange(user: AdminUser, event: Event) {
    const planId = (event.target as HTMLSelectElement).value;
    if (!planId) return;
    this.error.set('');
    this.busy.set(true);
    this.adminService.assignPlan(user.id, planId).subscribe({
      next: updated => { this.applyUpdate(updated); this.busy.set(false); },
      error: err => { this.error.set(humanizeError(err, this.i18n.t('admin_users_action_failed'))); this.busy.set(false); },
    });
  }

  async disableUser(user: AdminUser) {
    const ok = await this.confirmDialog.confirm({
      title: user.deleted ? this.i18n.t('admin_user_enable_title') : this.i18n.t('admin_user_disable_title'),
      message: user.deleted
        ? this.i18n.t('admin_user_enable_msg')
        : this.i18n.t('admin_user_disable_msg'),
      confirmText: user.deleted ? this.i18n.t('admin_user_enable_confirm') : this.i18n.t('admin_action_disable'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: user.deleted ? 'accent' : 'danger',
    });
    if (!ok) return;
    this.error.set('');
    this.busy.set(true);
    this.adminService.disableUser(user.id).subscribe({
      next: () => { this.busy.set(false); this.closeDetail(); this.loadUsers(); },
      error: err => { this.error.set(humanizeError(err, this.i18n.t('admin_users_action_failed'))); this.busy.set(false); },
    });
  }

  // ── Users support tooling actions ───────────────────────────────────
  async resetPassword(user: AdminUser) {
    if (!this.identity.has('USER_CREDENTIAL_RESET')) return;
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('admin_reset_password'),
      message: this.i18n.t('admin_reset_password_confirm'),
      confirmText: this.i18n.t('admin_reset_password'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'accent',
    });
    if (!ok) return;
    this.error.set('');
    this.busy.set(true);
    // NOTE: backend resetPasswordRequest() is audit-only for now — no email is sent and no reset
    // token is issued (no mail system yet). The flash says so; build the real flow once email exists.
    this.adminService.resetUserPassword(user.id).subscribe({
      next: () => { this.busy.set(false); this.showFlash(this.i18n.t('admin_reset_password_todo')); },
      error: err => { this.error.set(humanizeError(err, this.i18n.t('admin_users_action_failed'))); this.busy.set(false); },
    });
  }

  async revokeSessions(user: AdminUser) {
    if (!this.identity.has('USER_MANAGE')) return;
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('admin_revoke_sessions'),
      message: this.i18n.t('admin_revoke_sessions_confirm'),
      confirmText: this.i18n.t('admin_revoke_sessions'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!ok) return;
    this.error.set('');
    this.revoking.set(true);
    this.adminService.revokeUserSessions(user.id).subscribe({
      next: s => { this.sessionsCount.set(s.activeSessions); this.revoking.set(false); this.showFlash(this.i18n.t('admin_sessions_revoked')); },
      error: err => { this.error.set(humanizeError(err, this.i18n.t('admin_users_action_failed'))); this.revoking.set(false); },
    });
  }

  addNote(user: AdminUser) {
    const body = this.noteDraft().trim();
    if (!body || this.noteSubmitting()) return;
    this.error.set('');
    this.noteSubmitting.set(true);
    this.adminService.addUserNote(user.id, body).subscribe({
      next: note => {
        this.notesData.update(list => [note, ...(list ?? [])]);
        this.noteDraft.set('');
        this.noteSubmitting.set(false);
      },
      error: err => { this.error.set(humanizeError(err, this.i18n.t('admin_users_action_failed'))); this.noteSubmitting.set(false); },
    });
  }

  /** Whether the current staffer may delete the given note (author or USER_MANAGE). */
  canDeleteNote(note: StaffNote): boolean {
    return note.authorEmail === this.identity.identity()?.email || this.identity.has('USER_MANAGE');
  }

  async deleteNote(user: AdminUser, note: StaffNote) {
    if (!this.canDeleteNote(note)) return;
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('admin_note_delete_confirm'),
      message: note.body,
      confirmText: this.i18n.t('admin_org_delete'),
      cancelText: this.i18n.t('confirm_cancel'),
      tone: 'danger',
    });
    if (!ok) return;
    this.error.set('');
    this.deletingNoteId.set(note.id);
    this.adminService.deleteUserNote(user.id, note.id).subscribe({
      next: () => {
        this.notesData.update(list => (list ?? []).filter(n => n.id !== note.id));
        this.deletingNoteId.set(null);
      },
      error: err => { this.error.set(humanizeError(err, this.i18n.t('admin_users_action_failed'))); this.deletingNoteId.set(null); },
    });
  }

  onNoteInput(event: Event) {
    this.noteDraft.set((event.target as HTMLTextAreaElement).value);
  }
}
