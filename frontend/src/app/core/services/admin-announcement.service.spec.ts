import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AdminAnnouncementService } from './admin-announcement.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminAnnouncementService', () => {
  let api: MockApi;
  let service: AdminAnnouncementService;
  const base = '/admin/announcements';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminAnnouncementService);
  });

  it('list() GETs with pageable + filter params', () => {
    service.list({ status: 'PUBLISHED' } as never, 1, 25);
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe(base);
    const p = opts.params as HttpParams;
    expect(p.get('page')).toBe('1');
    expect(p.get('size')).toBe('25');
    expect(p.get('status')).toBe('PUBLISHED');
  });

  it('kpis()/get() hit the kpis and detail paths', () => {
    service.kpis();
    expect(api.get).toHaveBeenCalledWith(`${base}/kpis`);
    service.get('a1');
    expect(api.get).toHaveBeenCalledWith(`${base}/a1`);
  });

  it('create() POSTs and update() PUTs the body', () => {
    const body = { title: 'T' } as never;
    service.create(body);
    expect(api.post).toHaveBeenCalledWith(base, body);
    service.update('a1', body);
    expect(api.put).toHaveBeenCalledWith(`${base}/a1`, body);
  });

  it('lifecycle mutations POST to their sub-paths', () => {
    for (const op of ['publish', 'end', 'archive', 'duplicate'] as const) {
      (service[op] as (id: string) => unknown)('a1');
      expect(api.post).toHaveBeenCalledWith(`${base}/a1/${op}`, {});
    }
  });
});
