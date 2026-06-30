import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AdminGdprService } from './admin-gdpr.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminGdprService', () => {
  let api: MockApi;
  let service: AdminGdprService;
  const base = '/admin/gdpr';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminGdprService);
  });

  it('requests() GETs with pageable + filter params', () => {
    service.requests({ type: 'ERASURE' } as never, 1, 5);
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe(`${base}/requests`);
    const p = opts.params as HttpParams;
    expect(p.get('page')).toBe('1');
    expect(p.get('type')).toBe('ERASURE');
  });

  it('GETs kpis / retention / request detail', () => {
    service.kpis();
    expect(api.get).toHaveBeenCalledWith(`${base}/kpis`);
    service.retention();
    expect(api.get).toHaveBeenCalledWith(`${base}/retention`);
    service.getRequest('r1');
    expect(api.get).toHaveBeenCalledWith(`${base}/requests/r1`);
  });

  it('create() POSTs the body', () => {
    const body = { subjectEmail: 'a@b.c' } as never;
    service.create(body);
    expect(api.post).toHaveBeenCalledWith(`${base}/requests`, body);
  });

  it('moderation mutations POST to their sub-paths', () => {
    service.runExport('r1');
    expect(api.post).toHaveBeenCalledWith(`${base}/requests/r1/export`, {});
    service.executeErasure('r1');
    expect(api.post).toHaveBeenCalledWith(`${base}/requests/r1/erase`, {});
    service.reject('r1', 'not verified');
    expect(api.post).toHaveBeenCalledWith(`${base}/requests/r1/reject`, { reason: 'not verified' });
    service.markComplete('r1');
    expect(api.post).toHaveBeenCalledWith(`${base}/requests/r1/complete`, {});
  });
});
