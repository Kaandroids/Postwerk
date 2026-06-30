import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { OrganizationService } from './organization.service';
import { ROLE_PERMISSIONS } from '../../models/organization.model';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

const PERMS = ROLE_PERMISSIONS as unknown as Record<string, string[]>;

describe('OrganizationService', () => {
  let api: MockApi;
  let service: OrganizationService;

  beforeEach(() => {
    localStorage.clear();
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(OrganizationService);
  });

  it('can() is optimistic (true) before a role is known', () => {
    expect(service.can('ANYTHING' as never)).toBe(true);
  });

  it('can() reflects the active role permission bundle', () => {
    const role = Object.keys(PERMS).find(r => PERMS[r].length > 0)!;
    const granted = PERMS[role][0];
    service.orgs.set([{ id: 'o1', personal: false, myRole: role } as never]);
    service.activeOrgId.set('o1');
    expect(service.can(granted as never)).toBe(true);

    const all = new Set(Object.values(PERMS).flat());
    const missing = [...all].find(p => !PERMS[role].includes(p));
    if (missing) expect(service.can(missing as never)).toBe(false);
  });

  it('loadOrgs() stores orgs and picks the personal org as active (persisted)', () => {
    api.get.mockReturnValue(of([{ id: 'p', personal: true }, { id: 'o', personal: false }]));
    service.loadOrgs();
    expect(api.get).toHaveBeenCalledWith('/organizations');
    expect(service.orgs().length).toBe(2);
    expect(service.activeOrgId()).toBe('p');
    expect(localStorage.getItem('postwerk.activeOrgId')).toBe('p');
  });

  it('create() POSTs and appends the new org to the switcher list', () => {
    api.post.mockReturnValue(of({ id: 'new', personal: false }));
    service.create({ name: 'X' } as never).subscribe();
    expect(api.post).toHaveBeenCalledWith('/organizations', { name: 'X' });
    expect(service.orgs().some(o => o.id === 'new')).toBe(true);
  });

  it('current()/invite()/leave() hit their endpoints', () => {
    service.current();
    expect(api.get).toHaveBeenCalledWith('/organizations/current');
    const req = { email: 'a@b.c' } as never;
    service.invite(req);
    expect(api.post).toHaveBeenCalledWith('/organizations/members', req);
    service.leave();
    expect(api.post).toHaveBeenCalledWith('/organizations/leave', {});
  });

  it('loadInvitations() stores the pending invitations', () => {
    api.get.mockReturnValue(of([{ organizationId: 'o1' }]));
    service.loadInvitations();
    expect(service.invitations().length).toBe(1);
  });

  it('acceptInvitation() removes the invite and adds the joined org', () => {
    service.invitations.set([{ organizationId: 'o1' } as never]);
    api.post.mockReturnValue(of({ id: 'o1' }));
    service.acceptInvitation('o1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/organizations/invitations/o1/accept', {});
    expect(service.invitations().length).toBe(0);
    expect(service.orgs().some(o => o.id === 'o1')).toBe(true);
  });

  it('declineInvitation() removes the invite', () => {
    service.invitations.set([{ organizationId: 'o1' } as never]);
    api.post.mockReturnValue(of(undefined));
    service.declineInvitation('o1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/organizations/invitations/o1/decline', {});
    expect(service.invitations().length).toBe(0);
  });

  it('member + mailbox-grant endpoints', () => {
    service.setRole('u1', 'ADMIN' as never);
    expect(api.put).toHaveBeenCalledWith('/organizations/members/u1', { role: 'ADMIN' });
    service.removeMember('u1');
    expect(api.delete).toHaveBeenCalledWith('/organizations/members/u1');
    service.getMailboxGrants('u1');
    expect(api.get).toHaveBeenCalledWith('/organizations/members/u1/mailbox-grants');
    const grants = [{ accountId: 'a' }] as never;
    service.setMailboxGrants('u1', grants);
    expect(api.put).toHaveBeenCalledWith('/organizations/members/u1/mailbox-grants', grants);
  });

  it('switchOrg() is a no-op for the current or an unknown org (no reload)', () => {
    service.orgs.set([{ id: 'o1' } as never]);
    service.activeOrgId.set('o1');
    service.switchOrg('o1');     // same → no-op
    service.switchOrg('missing'); // unknown → no-op
    expect(service.activeOrgId()).toBe('o1');
  });
});
