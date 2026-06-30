import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { OrgMembersComponent } from './org-members.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { UserService } from '../../../../core/services/user.service';
import { OrganizationService } from '../../../../core/services/organization.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

function orgStub() {
  return {
    current: vi.fn(() => of({ members: [], personal: false, myRole: 'OWNER', name: 'Org' })),
    invite: vi.fn(() => of({})),
    setRole: vi.fn(() => of({})),
    removeMember: vi.fn(() => of(undefined)),
    leave: vi.fn(() => of(undefined)),
    getMailboxGrants: vi.fn(() => of([])),
    setMailboxGrants: vi.fn(() => of(undefined)),
    orgs: signal([{ id: 'p', personal: true }]),
    switchOrg: vi.fn(),
  };
}

describe('OrgMembersComponent', () => {
  let org: ReturnType<typeof orgStub>;
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: OrgMembersComponent;

  beforeEach(() => {
    org = orgStub();
    confirm = { confirm: vi.fn() };
    TestBed.configureTestingModule({
      imports: [OrgMembersComponent],
      providers: [
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: UserService, useValue: { profile: signal({ id: 'me' }) } },
        { provide: OrganizationService, useValue: org },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(OrgMembersComponent).componentInstance;
  });

  it('canManage is true only for OWNER/ADMIN', () => {
    cmp.detail.set({ myRole: 'OWNER', members: [] } as never);
    expect(cmp.canManage()).toBe(true);
    cmp.detail.set({ myRole: 'EDITOR', members: [] } as never);
    expect(cmp.canManage()).toBe(false);
  });

  it('members / isPersonal / myUserId reflect the loaded detail', () => {
    cmp.detail.set({ members: [{ userId: 'u1' }], personal: true } as never);
    expect(cmp.members().length).toBe(1);
    expect(cmp.isPersonal()).toBe(true);
    expect(cmp.myUserId()).toBe('me');
  });

  it('isMe matches the current user, hasGrantEditor excludes owners/admins', () => {
    expect(cmp.isMe({ userId: 'me' } as never)).toBe(true);
    expect(cmp.isMe({ userId: 'other' } as never)).toBe(false);
    expect(cmp.hasGrantEditor({ role: 'OWNER' } as never)).toBe(false);
    expect(cmp.hasGrantEditor({ role: 'MEMBER' } as never)).toBe(true);
  });

  it('load() fills the detail and clears loading', () => {
    cmp.load();
    expect(org.current).toHaveBeenCalled();
    expect(cmp.detail()?.name).toBe('Org');
    expect(cmp.loading()).toBe(false);
  });

  it('invite() ignores empty input and otherwise posts + resets', () => {
    cmp.inviteEmail.set('   ');
    cmp.invite();
    expect(org.invite).not.toHaveBeenCalled();

    cmp.inviteEmail.set('new@x.com');
    cmp.inviteRole.set('EDITOR');
    cmp.invite();
    expect(org.invite).toHaveBeenCalledWith({ email: 'new@x.com', role: 'EDITOR' });
    expect(cmp.inviteEmail()).toBe('');
    expect(cmp.inviteRole()).toBe('MEMBER');
  });

  it('changeRole() skips a no-op and otherwise updates the member in place', () => {
    cmp.detail.set({ members: [{ userId: 'u1', role: 'MEMBER' }] } as never);
    cmp.changeRole({ userId: 'u1', role: 'MEMBER' } as never, { target: { value: 'MEMBER' } } as unknown as Event);
    expect(org.setRole).not.toHaveBeenCalled();

    org.setRole.mockReturnValue(of({ userId: 'u1', role: 'ADMIN' }));
    cmp.changeRole({ userId: 'u1', role: 'MEMBER' } as never, { target: { value: 'ADMIN' } } as unknown as Event);
    expect(org.setRole).toHaveBeenCalledWith('u1', 'ADMIN');
    expect(cmp.members()[0].role).toBe('ADMIN');
  });

  it('removeMember() runs only after confirmation', async () => {
    confirm.confirm.mockResolvedValue(false);
    await cmp.removeMember({ userId: 'u1', email: 'a@b.c' } as never);
    expect(org.removeMember).not.toHaveBeenCalled();

    confirm.confirm.mockResolvedValue(true);
    await cmp.removeMember({ userId: 'u1', email: 'a@b.c' } as never);
    expect(org.removeMember).toHaveBeenCalledWith('u1');
  });

  it('leave() switches to the personal org after confirmation', async () => {
    confirm.confirm.mockResolvedValue(true);
    await cmp.leave();
    expect(org.leave).toHaveBeenCalled();
    expect(org.switchOrg).toHaveBeenCalledWith('p');
  });

  it('openGrants() loads the member grants into the drawer', () => {
    org.getMailboxGrants.mockReturnValue(of([{ mailboxId: 'm', canRead: true, canSend: false }]) as never);
    cmp.openGrants({ userId: 'u1' } as never);
    expect(cmp.grantMember()?.userId).toBe('u1');
    expect(cmp.grants().length).toBe(1);
    expect(cmp.grantsLoading()).toBe(false);
  });

  it('toggleGrant() enforces the send-implies-read invariant', () => {
    cmp.grants.set([{ mailboxId: 'm', canRead: true, canSend: true }] as never);
    cmp.toggleGrant('m', 'read'); // clearing read clears send too
    expect(cmp.grants()[0]).toMatchObject({ canRead: false, canSend: false });

    cmp.grants.set([{ mailboxId: 'm', canRead: false, canSend: false }] as never);
    cmp.toggleGrant('m', 'send'); // enabling send enables read
    expect(cmp.grants()[0]).toMatchObject({ canRead: true, canSend: true });
  });

  it('saveGrants() persists and closes the drawer', () => {
    cmp.grantMember.set({ userId: 'u1' } as never);
    cmp.grants.set([{ mailboxId: 'm', canRead: true, canSend: false }] as never);
    cmp.saveGrants();
    expect(org.setMailboxGrants).toHaveBeenCalledWith('u1', [{ mailboxId: 'm', canRead: true, canSend: false }]);
    expect(cmp.grantMember()).toBeNull();
  });

  it('roleLabel maps a role to its i18n key', () => {
    expect(cmp.roleLabel('ADMIN' as never)).toBe('org_role_admin');
  });
});
