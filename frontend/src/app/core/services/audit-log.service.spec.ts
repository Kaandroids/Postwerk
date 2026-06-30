import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AuditLogService } from './audit-log.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AuditLogService', () => {
  let api: MockApi;
  let service: AuditLogService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AuditLogService);
  });

  it('list() GETs with default paging and no action filter', () => {
    service.list();
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe('/audit-logs');
    const p = opts.params as HttpParams;
    expect(p.get('page')).toBe('0');
    expect(p.get('size')).toBe('20');
    expect(p.get('action')).toBeNull();
  });

  it('list() forwards paging and the action filter', () => {
    service.list(2, 50, 'DELETE');
    const p = api.get.mock.calls[0][1].params as HttpParams;
    expect(p.get('page')).toBe('2');
    expect(p.get('size')).toBe('50');
    expect(p.get('action')).toBe('DELETE');
  });
});
