import { TestBed } from '@angular/core/testing';
import { AdminBackgroundJobsService } from './admin-background-jobs.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminBackgroundJobsService', () => {
  let api: MockApi;
  let service: AdminBackgroundJobsService;
  const base = '/admin/background-jobs';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminBackgroundJobsService);
  });

  it('GETs jobs / kpis / queues / job detail', () => {
    service.jobs();
    expect(api.get).toHaveBeenCalledWith(`${base}/jobs`);
    service.kpis();
    expect(api.get).toHaveBeenCalledWith(`${base}/kpis`);
    service.queues();
    expect(api.get).toHaveBeenCalledWith(`${base}/queues`);
    service.getJob('j1');
    expect(api.get).toHaveBeenCalledWith(`${base}/jobs/j1`);
  });

  it('run/pause/resume POST to the job sub-paths', () => {
    service.runNow('j1');
    expect(api.post).toHaveBeenCalledWith(`${base}/jobs/j1/run`, {});
    service.pause('j1');
    expect(api.post).toHaveBeenCalledWith(`${base}/jobs/j1/pause`, {});
    service.resume('j1');
    expect(api.post).toHaveBeenCalledWith(`${base}/jobs/j1/resume`, {});
  });
});
