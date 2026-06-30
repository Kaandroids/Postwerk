import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AdminSubscriptionService } from './admin-subscription.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminSubscriptionService', () => {
  let api: MockApi;
  let service: AdminSubscriptionService;
  const base = '/admin/subscriptions';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminSubscriptionService);
  });

  it('list() GETs with pageable + filter params', () => {
    service.list({ plan: 'pro' } as never, 0, 10);
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe(base);
    expect((opts.params as HttpParams).get('plan')).toBe('pro');
  });

  it('kpis()/get()/planHistory() hit kpis, detail and history', () => {
    service.kpis();
    expect(api.get).toHaveBeenCalledWith(`${base}/kpis`);
    service.get('o1');
    expect(api.get).toHaveBeenCalledWith(`${base}/o1`);
    service.planHistory('o1');
    expect(api.get).toHaveBeenCalledWith(`${base}/o1/plan-history`);
  });

  it('changePlan() PATCHes the plan + reason', () => {
    service.changePlan('o1', 'p2', 'upsell');
    expect(api.patch).toHaveBeenCalledWith(`${base}/o1/plan`, { planId: 'p2', reason: 'upsell' });
  });
});
