import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AdminFeatureFlagService } from './admin-feature-flag.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminFeatureFlagService', () => {
  let api: MockApi;
  let service: AdminFeatureFlagService;
  const base = '/admin/feature-flags';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminFeatureFlagService);
  });

  it('list() GETs with pageable + filter params', () => {
    service.list({ state: 'ENABLED' } as never, 0, 10);
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe(base);
    expect((opts.params as HttpParams).get('state')).toBe('ENABLED');
  });

  it('kpis()/get() hit kpis and detail', () => {
    service.kpis();
    expect(api.get).toHaveBeenCalledWith(`${base}/kpis`);
    service.get('f1');
    expect(api.get).toHaveBeenCalledWith(`${base}/f1`);
  });

  it('create() POSTs and update() PUTs the body', () => {
    const body = { key: 'beta' } as never;
    service.create(body);
    expect(api.post).toHaveBeenCalledWith(base, body);
    service.update('f1', body);
    expect(api.put).toHaveBeenCalledWith(`${base}/f1`, body);
  });

  it('lifecycle mutations POST to their sub-paths', () => {
    for (const op of ['enable', 'disable', 'kill', 'restore', 'archive', 'duplicate'] as const) {
      (service[op] as (id: string) => unknown)('f1');
      expect(api.post).toHaveBeenCalledWith(`${base}/f1/${op}`, {});
    }
  });
});
