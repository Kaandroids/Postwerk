import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AdminOrganizationService } from './admin-organization.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminOrganizationService', () => {
  let api: MockApi;
  let service: AdminOrganizationService;
  const base = '/admin/organizations';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminOrganizationService);
  });

  it('list() sets search/page/size, omitting personal when undefined', () => {
    service.list();
    const p = api.get.mock.calls[0][1].params as HttpParams;
    expect(p.get('search')).toBe('');
    expect(p.get('page')).toBe('0');
    expect(p.get('personal')).toBeNull();
  });

  it('list() includes the personal filter when provided', () => {
    service.list('acme', true, 1, 20);
    const p = api.get.mock.calls[0][1].params as HttpParams;
    expect(p.get('search')).toBe('acme');
    expect(p.get('personal')).toBe('true');
  });

  it('get()/getAutomations()/getMailboxes() hit the org detail tabs', () => {
    service.get('o1');
    expect(api.get).toHaveBeenCalledWith(`${base}/o1`);
    service.getAutomations('o1');
    expect(api.get).toHaveBeenCalledWith(`${base}/o1/automations`);
    service.getMailboxes('o1');
    expect(api.get).toHaveBeenCalledWith(`${base}/o1/mailboxes`);
  });

  it('suspend() POSTs the reason (null when omitted) and activate() POSTs empty', () => {
    service.suspend('o1');
    expect(api.post).toHaveBeenCalledWith(`${base}/o1/suspend`, { reason: null });
    service.suspend('o1', 'abuse');
    expect(api.post).toHaveBeenCalledWith(`${base}/o1/suspend`, { reason: 'abuse' });
    service.activate('o1');
    expect(api.post).toHaveBeenCalledWith(`${base}/o1/activate`, {});
  });

  it('transferOwnership() POSTs the new owner and remove() DELETEs', () => {
    service.transferOwnership('o1', 'u9');
    expect(api.post).toHaveBeenCalledWith(`${base}/o1/transfer-ownership`, { newOwnerUserId: 'u9' });
    service.remove('o1');
    expect(api.delete).toHaveBeenCalledWith(`${base}/o1`);
  });
});
