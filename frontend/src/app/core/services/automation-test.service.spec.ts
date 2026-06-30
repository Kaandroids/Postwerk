import { TestBed } from '@angular/core/testing';
import { AutomationTestService, AutomationTestCaseRequest } from './automation-test.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AutomationTestService', () => {
  let api: MockApi;
  let service: AutomationTestService;
  const a = 'auto1';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AutomationTestService);
  });

  const req = { name: 'T', description: null, emailInput: {}, assertions: [] } as unknown as AutomationTestCaseRequest;

  it('getTests() GETs the tests collection', () => {
    service.getTests(a);
    expect(api.get).toHaveBeenCalledWith(`/automations/${a}/tests`);
  });

  it('createTest() POSTs the request', () => {
    service.createTest(a, req);
    expect(api.post).toHaveBeenCalledWith(`/automations/${a}/tests`, req);
  });

  it('updateTest() PUTs to the test id path', () => {
    service.updateTest(a, 't1', req);
    expect(api.put).toHaveBeenCalledWith(`/automations/${a}/tests/t1`, req);
  });

  it('deleteTest() DELETEs the test', () => {
    service.deleteTest(a, 't1');
    expect(api.delete).toHaveBeenCalledWith(`/automations/${a}/tests/t1`);
  });

  it('getLatestResult() GETs the latest-result sub-path', () => {
    service.getLatestResult(a, 't1');
    expect(api.get).toHaveBeenCalledWith(`/automations/${a}/tests/t1/latest-result`);
  });

  it('runTest() POSTs to the run sub-path', () => {
    service.runTest(a, 't1');
    expect(api.post).toHaveBeenCalledWith(`/automations/${a}/tests/t1/run`, {});
  });

  it('runAllTests() POSTs to the run-all sub-path', () => {
    service.runAllTests(a);
    expect(api.post).toHaveBeenCalledWith(`/automations/${a}/tests/run-all`, {});
  });
});
