import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AdminEmailHealthService } from './admin-email-health.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminEmailHealthService', () => {
  let api: MockApi;
  let service: AdminEmailHealthService;
  const base = '/admin/email-health';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminEmailHealthService);
  });

  it('listMailboxes() GETs with pageable + filter params', () => {
    service.listMailboxes({ status: 'ERROR' } as never, 2, 15);
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe(`${base}/mailboxes`);
    const p = opts.params as HttpParams;
    expect(p.get('page')).toBe('2');
    expect(p.get('size')).toBe('15');
    expect(p.get('status')).toBe('ERROR');
  });

  it('GETs kpis / clusters / mailbox detail', () => {
    service.kpis();
    expect(api.get).toHaveBeenCalledWith(`${base}/kpis`);
    service.clusters();
    expect(api.get).toHaveBeenCalledWith(`${base}/clusters`);
    service.getMailbox('m1');
    expect(api.get).toHaveBeenCalledWith(`${base}/mailboxes/m1`);
  });

  it('resync/pause/resume POST to the mailbox sub-paths', () => {
    service.resync('m1');
    expect(api.post).toHaveBeenCalledWith(`${base}/mailboxes/m1/resync`, {});
    service.pause('m1');
    expect(api.post).toHaveBeenCalledWith(`${base}/mailboxes/m1/pause`, {});
    service.resume('m1');
    expect(api.post).toHaveBeenCalledWith(`${base}/mailboxes/m1/resume`, {});
  });
});
