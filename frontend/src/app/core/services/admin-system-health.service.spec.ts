import { TestBed } from '@angular/core/testing';
import { AdminSystemHealthService } from './admin-system-health.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminSystemHealthService', () => {
  let api: MockApi;
  let service: AdminSystemHealthService;
  const base = '/admin/system-health';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminSystemHealthService);
  });

  it('GETs subsystems / kpis / events / subsystem detail', () => {
    service.subsystems();
    expect(api.get).toHaveBeenCalledWith(`${base}/subsystems`);
    service.kpis();
    expect(api.get).toHaveBeenCalledWith(`${base}/kpis`);
    service.events();
    expect(api.get).toHaveBeenCalledWith(`${base}/events`);
    service.getSubsystem('s1');
    expect(api.get).toHaveBeenCalledWith(`${base}/subsystems/s1`);
  });

  it('probe() and flushCache() POST to their sub-paths', () => {
    service.probe('s1');
    expect(api.post).toHaveBeenCalledWith(`${base}/subsystems/s1/probe`, {});
    service.flushCache();
    expect(api.post).toHaveBeenCalledWith(`${base}/cache/flush`, {});
  });

  it('maintenance mode: GET current + PUT toggle', () => {
    service.getMaintenance();
    expect(api.get).toHaveBeenCalledWith(`${base}/maintenance`);
    service.setMaintenance(true, 'Back at 5pm');
    expect(api.put).toHaveBeenCalledWith(`${base}/maintenance`, { enabled: true, message: 'Back at 5pm' });
  });
});
