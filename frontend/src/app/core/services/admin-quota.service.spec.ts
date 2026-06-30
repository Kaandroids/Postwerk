import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AdminQuotaService } from './admin-quota.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminQuotaService', () => {
  let api: MockApi;
  let service: AdminQuotaService;
  const base = '/admin/quota-overrides';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminQuotaService);
  });

  it('list() GETs with pageable + filter params', () => {
    service.list({ scope: 'USER' } as never, 0, 10);
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe(base);
    expect((opts.params as HttpParams).get('scope')).toBe('USER');
  });

  it('kpis() GETs the kpis sub-path', () => {
    service.kpis();
    expect(api.get).toHaveBeenCalledWith(`${base}/kpis`);
  });

  it('create() POSTs, update() PUTs, revoke() DELETEs', () => {
    const req = { tokens: 1000 } as never;
    service.create(req);
    expect(api.post).toHaveBeenCalledWith(base, req);
    service.update('q1', req);
    expect(api.put).toHaveBeenCalledWith(`${base}/q1`, req);
    service.revoke('q1');
    expect(api.delete).toHaveBeenCalledWith(`${base}/q1`);
  });
});
