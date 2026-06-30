import { TestBed } from '@angular/core/testing';
import { ActivityService } from './activity.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('ActivityService', () => {
  let api: MockApi;
  let service: ActivityService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(ActivityService);
  });

  it('recent() GETs the first page by default', () => {
    service.recent();
    expect(api.get).toHaveBeenCalledWith('/activity?page=0');
  });

  it('recent(n) GETs the requested page', () => {
    service.recent(3);
    expect(api.get).toHaveBeenCalledWith('/activity?page=3');
  });
});
