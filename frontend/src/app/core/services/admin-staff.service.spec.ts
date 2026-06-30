import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AdminStaffService } from './admin-staff.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminStaffService', () => {
  let api: MockApi;
  let service: AdminStaffService;
  const base = '/admin/staff';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminStaffService);
  });

  it('roster() GETs with pageable + filter params', () => {
    service.roster({ role: 'SUPPORT' } as never, 0, 10);
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe(base);
    expect((opts.params as HttpParams).get('role')).toBe('SUPPORT');
  });

  it('kpis()/roles() hit kpis and the role matrix', () => {
    service.kpis();
    expect(api.get).toHaveBeenCalledWith(`${base}/kpis`);
    service.roles();
    expect(api.get).toHaveBeenCalledWith(`${base}/roles`);
  });

  it('candidates() GETs with the search param', () => {
    service.candidates('jane');
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe(`${base}/candidates`);
    expect((opts.params as HttpParams).get('search')).toBe('jane');
  });

  it('setRole() POSTs the role and revoke() DELETEs the member', () => {
    service.setRole('u1', 'BILLING');
    expect(api.post).toHaveBeenCalledWith(`${base}/u1`, { role: 'BILLING' });
    service.revoke('u1');
    expect(api.delete).toHaveBeenCalledWith(`${base}/u1`);
  });
});
