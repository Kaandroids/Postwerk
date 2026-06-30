import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { SidebarComponent } from './sidebar.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { TokenService } from '../../../../core/services/token.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { UserService } from '../../../../core/services/user.service';
import { WorkspaceService } from '../../../../core/services/workspace.service';
import { OrganizationService } from '../../../../core/services/organization.service';
import { FolderService } from '../../../../core/services/folder.service';
import { AuthService } from '../../../auth/services/auth.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

interface Access { syncActiveFromUrl(url: string): void }

// Built per-test (not in beforeEach): the nav-visibility computeds read can()/has() — plain
// functions, not signals — so they memoize on first render. Tests that need a different
// permission result must configure it BEFORE the component renders.
let token: { isAdmin: ReturnType<typeof vi.fn>; getRole: ReturnType<typeof vi.fn> };
let adminIdentity: { staffRole: ReturnType<typeof vi.fn>; has: ReturnType<typeof vi.fn> };
let org: { can: ReturnType<typeof vi.fn> };
let workspace: {
  folders: ReturnType<typeof signal>; activeAccount: ReturnType<typeof vi.fn>;
  addFolder: ReturnType<typeof vi.fn>; removeFolder: ReturnType<typeof vi.fn>; loadFolders: ReturnType<typeof vi.fn>;
};
let folderSvc: { createFolder: ReturnType<typeof vi.fn>; deleteFolder: ReturnType<typeof vi.fn> };
let auth: { logout: ReturnType<typeof vi.fn> };
let confirm: { confirm: ReturnType<typeof vi.fn> };
let router: Router;
let cmp: SidebarComponent;
let acc: Access;

function build(opts: { can?: (p: string) => boolean; has?: (p: string) => boolean } = {}) {
  token = { isAdmin: vi.fn(() => false), getRole: vi.fn(() => 'USER') };
  adminIdentity = { staffRole: vi.fn(() => null), has: vi.fn(opts.has ?? (() => true)) };
  org = { can: vi.fn(opts.can ?? (() => true)) };
  workspace = {
    folders: signal([{ role: 'INBOX', unreadCount: 5 }, { role: 'OTHER', id: 'f1', name: 'Custom' }]),
    activeAccount: vi.fn(() => ({ id: 'acc' })),
    addFolder: vi.fn(), removeFolder: vi.fn(), loadFolders: vi.fn(),
  };
  folderSvc = { createFolder: vi.fn(() => of({ id: 'f9' })), deleteFolder: vi.fn(() => of(undefined)) };
  auth = { logout: vi.fn(() => Promise.resolve()) };
  confirm = { confirm: vi.fn() };
  TestBed.configureTestingModule({
    imports: [SidebarComponent],
    providers: [
      provideRouter([]),
      { provide: I18nService, useValue: { t: (k: string) => k } },
      { provide: TokenService, useValue: token },
      { provide: AdminIdentityService, useValue: adminIdentity },
      { provide: UserService, useValue: { profile: signal({ fullName: 'John Doe' }) } },
      { provide: WorkspaceService, useValue: workspace },
      { provide: OrganizationService, useValue: org },
      { provide: FolderService, useValue: folderSvc },
      { provide: AuthService, useValue: auth },
      { provide: ConfirmDialogService, useValue: confirm },
    ],
  });
  cmp = TestBed.createComponent(SidebarComponent).componentInstance;
  router = TestBed.inject(Router);
  vi.spyOn(router, 'navigate').mockResolvedValue(true);
  acc = cmp as unknown as Access;
}

describe('SidebarComponent', () => {
  it('userInitials builds two letters from the profile name', () => {
    build();
    expect(cmp.userInitials()).toBe('JD');
  });

  it('roleLabel uses the platform role on the user side and the staff role in admin mode', () => {
    build();
    expect(cmp.roleLabel()).toBe('dash_role_user');
    adminIdentity.staffRole.mockReturnValue('SUPER_ADMIN');
    acc.syncActiveFromUrl('/dashboard/admin');
    expect(cmp.isAdminMode()).toBe(true);
    expect(cmp.roleLabel()).toBe('Super Admin');
  });

  it('getUnreadForKey maps a nav key to its folder unread count', () => {
    build();
    expect(cmp.getUnreadForKey('emails')).toBe(5);
    expect(cmp.getUnreadForKey('unknown')).toBe(0);
  });

  it('customFolders surfaces only OTHER-role folders', () => {
    build();
    expect(cmp.customFolders().map(f => f.id)).toEqual(['f1']);
  });

  it('nav permission filtering keeps everything when the role allows it', () => {
    build();
    expect(cmp.visibleAutomationNav().length).toBe(cmp.automationNav.length);
  });

  it('nav permission filtering drops items the role cannot use', () => {
    build({ can: (p) => p !== 'APPROVAL_VIEW' });
    expect(cmp.visibleAutomationNav().some(i => i.key === 'approvals')).toBe(false);
  });

  it('visibleAdminGroups lists the groups the staff role can access', () => {
    build();
    expect(cmp.visibleAdminGroups().length).toBeGreaterThan(0);
  });

  it('visibleAdminGroups hides everything without the staff permission', () => {
    build({ has: () => false });
    expect(cmp.visibleAdminGroups().length).toBe(0);
  });

  it('navigate routes home, folder keys, and plain keys', () => {
    build();
    cmp.navigate('home');
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    cmp.navigate('emails');
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard/emails'], { queryParams: { folder: 'INBOX' } });
    cmp.navigate('automations');
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard/automations']);
  });

  it('toggleGroup / isGroupExpanded track expansion state', () => {
    build();
    const item = { key: 'email', children: [{ key: 'emails' }] } as never;
    expect(cmp.isGroupExpanded(item)).toBe(false);
    cmp.toggleGroup(item);
    expect(cmp.isGroupExpanded(item)).toBe(true);
    cmp.toggleGroup(item);
    expect(cmp.isGroupExpanded(item)).toBe(false);
  });

  it('isGroupActive reflects whether a child is the active route', () => {
    build();
    cmp.active.set('emails');
    expect(cmp.isGroupActive({ children: [{ key: 'emails' }] } as never)).toBe(true);
    expect(cmp.isGroupActive({ children: [{ key: 'sent' }] } as never)).toBe(false);
  });

  it('confirmCreateFolder creates the folder and adds it to the workspace', () => {
    build();
    cmp.confirmCreateFolder({ value: '   ' } as HTMLInputElement); // blank → no-op
    expect(folderSvc.createFolder).not.toHaveBeenCalled();
    cmp.confirmCreateFolder({ value: 'Archive' } as HTMLInputElement);
    expect(folderSvc.createFolder).toHaveBeenCalledWith('acc', 'Archive');
    expect(workspace.addFolder).toHaveBeenCalledWith({ id: 'f9' });
  });

  it('deleteFolder removes after confirmation', async () => {
    build();
    confirm.confirm.mockResolvedValue(true);
    await cmp.deleteFolder({ stopPropagation: vi.fn() } as unknown as Event, { id: 'f1', name: 'Custom' });
    expect(workspace.removeFolder).toHaveBeenCalledWith('f1');
    expect(folderSvc.deleteFolder).toHaveBeenCalledWith('acc', 'f1');
  });

  it('syncActiveFromUrl parses dashboard / folder / admin URLs', () => {
    build();
    acc.syncActiveFromUrl('/dashboard/automations');
    expect(cmp.active()).toBe('automations');

    acc.syncActiveFromUrl('/dashboard/emails?folder=INBOX');
    expect(cmp.active()).toBe('emails');
    expect(cmp.activeFolder()).toBe('INBOX');

    acc.syncActiveFromUrl('/dashboard/emails?folder=Custom');
    expect(cmp.active()).toBe('folders');
    expect(cmp.foldersExpanded()).toBe(true);

    acc.syncActiveFromUrl('/dashboard/admin/users');
    expect(cmp.active()).toBe('admin/users');
  });

  it('logout clears the session and returns to login', async () => {
    build();
    cmp.logout();
    await Promise.resolve();
    expect(auth.logout).toHaveBeenCalled();
    await Promise.resolve();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
  });
});
