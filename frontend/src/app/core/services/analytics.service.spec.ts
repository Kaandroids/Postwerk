import { TestBed } from '@angular/core/testing';
import { AnalyticsService } from './analytics.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AnalyticsService', () => {
  let api: MockApi;
  let service: AnalyticsService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AnalyticsService);
  });

  it('overview() GETs the overview with the range query', () => {
    service.overview('30d' as never);
    expect(api.get).toHaveBeenCalledWith('/analytics/overview?range=30d');
  });

  it('automationDetail() GETs the per-automation detail with the range query', () => {
    service.automationDetail('a1', '7d' as never);
    expect(api.get).toHaveBeenCalledWith('/analytics/automations/a1?range=7d');
  });
});
