import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal, computed, ElementRef, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { I18nService } from '../../../../core/services/i18n.service';
import { TokenService } from '../../../../core/services/token.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { UserService } from '../../../../core/services/user.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { OrganizationService } from '../../../../core/services/organization.service';
import { FolderService } from '../../../../core/services/folder.service';
import { AuthService } from '../../../auth/services/auth.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { BrandComponent } from '../../../../shared/components/brand/brand.component';
import { ThemeToggleComponent } from '../../../../shared/components/theme-toggle/theme-toggle.component';
import { humanizeError } from '../../../../shared/utils/error.util';
import { NavItem } from '../../models/dashboard.models';

/** A single platform-staff admin nav destination, gated by a StaffPermission. */
interface AdminNavItem { key: string; labelKey: string; icon: string; meta?: string; perm: string; }
/** A labelled group of admin nav destinations (handoff taxonomy). */
interface AdminNavGroup { sectionKey: string; items: AdminNavItem[]; }

/** Collapsible sidebar navigation with main menu, folder tree, admin section, and user profile dropdown. */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, BrandComponent, ThemeToggleComponent],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  protected i18n = inject(I18nService);
  protected tokenService = inject(TokenService);
  private adminIdentity = inject(AdminIdentityService);
  protected userService = inject(UserService);
  protected workspace = inject(WorkspaceService);
  protected org = inject(OrganizationService);
  private router = inject(Router);
  private folderService = inject(FolderService);
  private authService = inject(AuthService);
  private confirmDialog = inject(ConfirmDialogService);
  private destroyRef = inject(DestroyRef);

  creatingFolder = signal(false);
  folderError = signal<string | null>(null);
  folderInput = viewChild<ElementRef<HTMLInputElement>>('folderInput');

  isAdmin = computed(() => this.tokenService.isAdmin());
  isAdminMode = computed(() => this.active().startsWith('admin'));

  userName = computed(() => this.userService.profile()?.fullName ?? '');
  userInitials = computed(() => {
    const name = this.userName();
    if (!name) return '';
    return name.split(' ').map(p => p[0]).join('').toUpperCase().slice(0, 2);
  });

  /** Footer role pill: staff role in admin mode, platform role on the user side. */
  roleLabel = computed(() => {
    if (this.isAdminMode()) {
      const sr = this.adminIdentity.staffRole();
      return sr ? this.humanizeRole(sr) : '';
    }
    return this.tokenService.getRole() === 'ADMIN' ? 'Admin' : this.i18n.t('dash_role_user');
  });

  private humanizeRole(role: string): string {
    return role.split('_')
      .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
      .join(' ');
  }

  active = signal('home');
  activeFolder = signal<string | null>(null);
  expandedGroups = signal<Set<string>>(new Set());
  foldersExpanded = signal(false);

  /** Maps sidebar nav keys to IMAP folder roles */
  private readonly folderKeyToRole: Record<string, string> = {
    emails: 'INBOX',
    sent: 'SENT',
    drafts: 'DRAFTS',
    spam: 'SPAM',
    trash: 'TRASH',
  };
  private readonly roleToFolderKey: Record<string, string> = Object.fromEntries(
    Object.entries(this.folderKeyToRole).map(([k, v]) => [v, k])
  );

  customFolders = computed(() =>
    this.workspace.folders().filter(f => f.role === 'OTHER')
  );

  getUnreadForKey(key: string): number {
    const role = this.folderKeyToRole[key];
    if (!role) return 0;
    const folder = this.workspace.folders().find(f => f.role === role);
    return folder?.unreadCount ?? 0;
  }

  // POSTFACH — scoped to the active email account (follows the account switcher).
  // Item perms mirror the backend OrgRole bundles so a role only sees what it can use.
  mainNav: NavItem[] = [
    { key: 'home', labelKey: 'nav_overview', icon: 'home' },
    {
      key: 'email', labelKey: 'nav_email_group', icon: 'campaigns',
      children: [
        { key: 'emails', labelKey: 'nav_inbox', perm: 'MAILBOX_READ' },
        { key: 'sent', labelKey: 'nav_sent', perm: 'MAILBOX_READ' },
        { key: 'drafts', labelKey: 'nav_drafts', perm: 'MAILBOX_READ' },
        { key: 'spam', labelKey: 'nav_spam', perm: 'MAILBOX_READ' },
        { key: 'trash', labelKey: 'nav_trash', perm: 'MAILBOX_READ' },
        { key: 'folders', labelKey: 'nav_folders', perm: 'MAILBOX_READ' },
        { key: 'email-settings', labelKey: 'nav_email_settings', perm: 'MAILBOX_CONNECT' },
      ],
    },
  ];

  // AUTOMATISIERUNG — org-scoped automation engine + monitoring (follows the org switcher)
  automationNav: NavItem[] = [
    { key: 'automations', labelKey: 'nav_automations', icon: 'automations', perm: 'AUTOMATION_VIEW' },
    { key: 'integrations', labelKey: 'nav_integrations', icon: 'integrations', perm: 'AUTOMATION_VIEW' },
    { key: 'activity', labelKey: 'nav_activity', icon: 'clock', perm: 'AUTOMATION_VIEW' },
    { key: 'approvals', labelKey: 'nav_approvals', icon: 'check', perm: 'APPROVAL_VIEW' },
    { key: 'analytics', labelKey: 'nav_analytics', icon: 'analytics', perm: 'AUTOMATION_VIEW' },
  ];

  // RESSOURCEN — org-scoped reusable building blocks used by automations
  resourcesNav: NavItem[] = [
    { key: 'templates', labelKey: 'nav_templates', icon: 'templates', perm: 'RESOURCE_VIEW' },
    { key: 'categories', labelKey: 'nav_categories', icon: 'tag', perm: 'RESOURCE_VIEW' },
    { key: 'parameter-sets', labelKey: 'nav_parameter_sets', icon: 'code', perm: 'RESOURCE_VIEW' },
    { key: 'knowledge-bases', labelKey: 'nav_knowledge_bases', icon: 'server', perm: 'RESOURCE_VIEW' },
    { key: 'secrets', labelKey: 'nav_secrets', icon: 'key', perm: 'SECRET_MANAGE' },
  ];

  marketplaceNav: NavItem[] = [
    { key: 'marketplace', labelKey: 'nav_marketplace', icon: 'market', perm: 'MARKETPLACE_INSTALL' },
    { key: 'marketplace-library', labelKey: 'nav_marketplace_library', icon: 'library', perm: 'MARKETPLACE_PUBLISH' },
  ];

  // VERWALTUNG — org administration + account management
  manageNav: NavItem[] = [
    { key: 'email-accounts', labelKey: 'nav_email_accounts', icon: 'mail', perm: 'MAILBOX_CONNECT' },
    { key: 'organization', labelKey: 'nav_organization', icon: 'building' },
    { key: 'plans', labelKey: 'nav_plans', icon: 'creditCard', perm: 'BILLING_MANAGE' },
    { key: 'audit-log', labelKey: 'nav_audit_log', icon: 'shield', perm: 'AUDIT_VIEW' },
    { key: 'settings', labelKey: 'nav_settings', icon: 'settings', perm: 'ORG_SETTINGS' },
  ];

  /** Filters a nav list to what the active-org role can use (group children filtered too; empty groups dropped). */
  private filterNav(items: NavItem[]): NavItem[] {
    const out: NavItem[] = [];
    for (const it of items) {
      if (it.children) {
        const children = it.children.filter(c => !c.perm || this.org.can(c.perm));
        if (children.length) out.push({ ...it, children });
      } else if (!it.perm || this.org.can(it.perm)) {
        out.push(it);
      }
    }
    return out;
  }

  visibleMainNav = computed(() => this.filterNav(this.mainNav));
  visibleAutomationNav = computed(() => this.filterNav(this.automationNav));
  visibleResourcesNav = computed(() => this.filterNav(this.resourcesNav));
  visibleMarketplaceNav = computed(() => this.filterNav(this.marketplaceNav));
  visibleManageNav = computed(() => this.filterNav(this.manageNav));

  // Platform-staff admin nav — grouped taxonomy (handoff). Shown in admin mode; reachable only by
  // staff (adminGuard). Destinations without a real page yet route to the "not built yet" placeholder.
  adminNavGroups: AdminNavGroup[] = [
    { sectionKey: 'adm_sec_overview', items: [
      { key: 'admin', labelKey: 'nav_admin_dashboard', icon: 'monitor', perm: 'PLATFORM_DASHBOARD_VIEW' },
    ]},
    { sectionKey: 'adm_sec_customers', items: [
      { key: 'admin/users', labelKey: 'nav_admin_users', icon: 'user', perm: 'USER_VIEW' },
      { key: 'admin/organizations', labelKey: 'nav_admin_organizations', icon: 'building', perm: 'ORG_VIEW' },
    ]},
    { sectionKey: 'adm_sec_billing', items: [
      { key: 'admin/plans-subscriptions', labelKey: 'nav_admin_plans_subs', icon: 'creditCard', meta: 'MRR', perm: 'PLAN_VIEW' },
    ]},
    { sectionKey: 'adm_sec_ai_cost', items: [
      { key: 'admin/ai-usage', labelKey: 'nav_admin_ai_usage', icon: 'sparkle', perm: 'AI_USAGE_VIEW' },
      { key: 'admin/automations', labelKey: 'nav_admin_automations', icon: 'automations', perm: 'AUTOMATION_OVERSIGHT_VIEW' },
      { key: 'admin/quota', labelKey: 'nav_admin_quota', icon: 'sliders', perm: 'AI_USAGE_VIEW' },
      { key: 'admin/pricing', labelKey: 'nav_admin_pricing', icon: 'creditCard', perm: 'PLAN_VIEW' },
    ]},
    { sectionKey: 'adm_sec_infra', items: [
      { key: 'admin/email-health', labelKey: 'nav_admin_email_health', icon: 'mailbox', meta: 'IMAP/SMTP', perm: 'INFRA_VIEW' },
      { key: 'admin/jobs', labelKey: 'nav_admin_jobs', icon: 'refresh', perm: 'INFRA_VIEW' },
      { key: 'admin/system-health', labelKey: 'nav_admin_system_health', icon: 'server', perm: 'INFRA_VIEW' },
    ]},
    { sectionKey: 'adm_sec_marketplace', items: [
      { key: 'admin/moderation', labelKey: 'nav_admin_moderation', icon: 'checkCircle', perm: 'MARKETPLACE_MODERATE' },
      { key: 'admin/reviews', labelKey: 'nav_admin_reviews', icon: 'star', perm: 'MARKETPLACE_MODERATE' },
    ]},
    { sectionKey: 'adm_sec_compliance', items: [
      { key: 'admin/gdpr', labelKey: 'nav_admin_gdpr', icon: 'lock', perm: 'COMPLIANCE_VIEW' },
      { key: 'admin/audit-log', labelKey: 'nav_admin_audit_log', icon: 'shield', perm: 'AUDIT_LOG_VIEW' },
    ]},
    { sectionKey: 'adm_sec_system', items: [
      { key: 'admin/flags', labelKey: 'nav_admin_flags', icon: 'toggleRight', perm: 'FEATURE_FLAG_MANAGE' },
      { key: 'admin/announcements', labelKey: 'nav_admin_announcements', icon: 'bell', perm: 'ANNOUNCEMENT_MANAGE' },
      { key: 'admin/staff', labelKey: 'nav_admin_staff', icon: 'key', perm: 'STAFF_MANAGE' },
    ]},
  ];

  /** Admin nav filtered to the destinations the caller's staff role can access (empty groups dropped). */
  visibleAdminGroups = computed(() =>
    this.adminNavGroups
      .map(g => ({ sectionKey: g.sectionKey, items: g.items.filter(it => this.adminIdentity.has(it.perm)) }))
      .filter(g => g.items.length > 0)
  );

  constructor() {
    this.syncActiveFromUrl(this.router.url);
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(e => this.syncActiveFromUrl(e.urlAfterRedirects));
  }

  navigate(key: string): void {
    if (key === 'home') {
      this.router.navigate(['/dashboard']);
      return;
    }

    const folder = this.folderKeyToRole[key];
    if (folder) {
      this.router.navigate(['/dashboard/emails'], { queryParams: { folder } });
      return;
    }

    this.router.navigate([`/dashboard/${key}`]);
  }

  navigateToFolder(folderName: string): void {
    this.router.navigate(['/dashboard/emails'], { queryParams: { folder: folderName } });
  }

  toggleFolders(): void {
    this.foldersExpanded.set(!this.foldersExpanded());
  }

  isGroupActive(item: NavItem): boolean {
    if (!item.children) return false;
    return item.children.some(c => c.key === this.active());
  }

  isGroupExpanded(item: NavItem): boolean {
    return this.expandedGroups().has(item.key);
  }

  toggleGroup(item: NavItem): void {
    const groups = new Set(this.expandedGroups());
    if (groups.has(item.key)) {
      groups.delete(item.key);
    } else {
      groups.add(item.key);
    }
    this.expandedGroups.set(groups);
  }

  startCreateFolder(event: Event): void {
    event.stopPropagation();
    this.foldersExpanded.set(true);
    this.creatingFolder.set(true);
    this.folderError.set(null);
    setTimeout(() => this.folderInput()?.nativeElement.focus());
  }

  confirmCreateFolder(input: HTMLInputElement): void {
    const name = input.value.trim();
    if (!name) {
      this.creatingFolder.set(false);
      this.folderError.set(null);
      return;
    }
    const account = this.workspace.activeAccount();
    if (!account) {
      this.creatingFolder.set(false);
      return;
    }
    this.folderError.set(null);
    this.folderService.createFolder(account.id, name).subscribe({
      next: (folder) => {
        this.creatingFolder.set(false);
        this.workspace.addFolder(folder);
      },
      error: (err) => {
        const serverMsg = humanizeError(err, '');
        const msg = serverMsg.includes('invalid characters')
          ? this.i18n.t('folders_invalid_chars')
          : this.i18n.t('folders_create_error');
        this.folderError.set(msg);
        setTimeout(() => this.folderInput()?.nativeElement.focus());
      },
    });
  }

  cancelCreateFolder(): void {
    this.creatingFolder.set(false);
    this.folderError.set(null);
  }

  async deleteFolder(event: Event, folder: { id: string; name: string }): Promise<void> {
    event.stopPropagation();
    const msg = this.i18n.t('folders_delete_confirm', { name: folder.name });
    const ok = await this.confirmDialog.confirm({
      title: this.i18n.t('confirm_delete'),
      message: msg,
      confirmText: this.i18n.t('confirm_delete'),
      cancelText: this.i18n.t('confirm_cancel'),
    });
    if (!ok) return;
    const account = this.workspace.activeAccount();
    if (!account) return;
    this.workspace.removeFolder(folder.id);
    this.folderService.deleteFolder(account.id, folder.id).subscribe({
      error: () => this.workspace.loadFolders(),
    });
  }

  logout(): void {
    this.authService.logout().finally(() => this.router.navigate(['/auth/login']));
  }

  private syncActiveFromUrl(url: string): void {
    const urlObj = new URL(url, 'http://localhost');
    const stripped = urlObj.pathname.replace('/dashboard', '').replace(/^\//, '');
    const folderParam = urlObj.searchParams.get('folder');

    // Handle admin sub-routes
    if (stripped.startsWith('admin')) {
      const adminPath = stripped === 'admin' ? 'admin' : stripped;
      this.activeFolder.set(null);
      this.active.set(adminPath);
      return;
    }

    const path = stripped.split('/')[0] || 'home';

    if (path === 'emails' && folderParam) {
      this.activeFolder.set(folderParam);
      const navKey = this.roleToFolderKey[folderParam];
      if (navKey) {
        this.active.set(navKey);
      } else {
        this.active.set('folders');
        this.foldersExpanded.set(true);
      }
    } else {
      this.activeFolder.set(null);
      this.active.set(path);
    }
  }
}
