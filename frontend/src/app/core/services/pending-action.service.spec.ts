import { TestBed } from '@angular/core/testing';
import { PendingActionService } from './pending-action.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('PendingActionService', () => {
  let api: MockApi;
  let service: PendingActionService;
  const base = '/pending-actions';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(PendingActionService);
  });

  it('list() GETs the collection, adding a status query only when given', () => {
    service.list();
    expect(api.get).toHaveBeenCalledWith(base);
    service.list('PENDING' as never);
    expect(api.get).toHaveBeenCalledWith(`${base}?status=PENDING`);
  });

  it('count() GETs the count sub-path', () => {
    service.count();
    expect(api.get).toHaveBeenCalledWith(`${base}/count`);
  });

  it('approve() POSTs to the approve sub-path', () => {
    service.approve('p1');
    expect(api.post).toHaveBeenCalledWith(`${base}/p1/approve`, {});
  });

  it('reject() POSTs, URL-encoding the optional note', () => {
    service.reject('p1');
    expect(api.post).toHaveBeenCalledWith(`${base}/p1/reject`, {});
    service.reject('p1', 'wrong category');
    expect(api.post).toHaveBeenCalledWith(`${base}/p1/reject?note=wrong%20category`, {});
  });

  it('reclassify() POSTs the categoryId query', () => {
    service.reclassify('p1', 'c9');
    expect(api.post).toHaveBeenCalledWith(`${base}/p1/reclassify?categoryId=c9`, {});
  });
});
