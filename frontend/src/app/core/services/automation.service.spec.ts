import { TestBed } from '@angular/core/testing';
import { AutomationService } from './automation.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

/**
 * Exemplar service spec: every method of an {@code ApiService}-backed service is exercised by
 * asserting the HTTP verb, path, and body it delegates to. No HTTP machinery — the verbs are
 * spies (see {@code createMockApi}). New {@code core/services} specs should follow this shape.
 */
describe('AutomationService', () => {
  let api: MockApi;
  let service: AutomationService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AutomationService);
  });

  it('list() GETs the automations collection', () => {
    service.list();
    expect(api.get).toHaveBeenCalledWith('/automations');
  });

  it('listIntegrations() GETs the integrations sub-collection', () => {
    service.listIntegrations();
    expect(api.get).toHaveBeenCalledWith('/automations/integrations');
  });

  it('get() GETs a single automation by id', () => {
    service.get('42');
    expect(api.get).toHaveBeenCalledWith('/automations/42');
  });

  it('create() POSTs the request', () => {
    const req = { name: 'A', kind: 'AUTOMATION' } as never;
    service.create(req);
    expect(api.post).toHaveBeenCalledWith('/automations', req);
  });

  it('update() PUTs the request to the id path', () => {
    const req = { name: 'B' } as never;
    service.update('7', req);
    expect(api.put).toHaveBeenCalledWith('/automations/7', req);
  });

  it('delete() DELETEs the id path', () => {
    service.delete('7');
    expect(api.delete).toHaveBeenCalledWith('/automations/7');
  });

  it('toggleLock() PATCHes the lock sub-path with an empty body', () => {
    service.toggleLock('9');
    expect(api.patch).toHaveBeenCalledWith('/automations/9/lock', {});
  });

  it('updateStatus() PATCHes the status sub-path with the status body', () => {
    service.updateStatus('9', 'ACTIVE' as never);
    expect(api.patch).toHaveBeenCalledWith('/automations/9/status', { status: 'ACTIVE' });
  });

  it('updateFlow() PUTs the flow sub-path', () => {
    const req = { nodes: [], edges: [] } as never;
    service.updateFlow('9', req);
    expect(api.put).toHaveBeenCalledWith('/automations/9/flow', req);
  });

  it('updateConstants() PUTs constants wrapped in a payload', () => {
    const constants = [{ name: 'x' }] as never;
    service.updateConstants('9', constants);
    expect(api.put).toHaveBeenCalledWith('/automations/9/constants', { constants });
  });

  it('getExecutions() GETs with paging params (defaults page=0,size=20)', () => {
    service.getExecutions('9');
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe('/automations/9/executions');
    expect(opts.params.get('page')).toBe('0');
    expect(opts.params.get('size')).toBe('20');
  });

  it('getExecutions() forwards explicit paging', () => {
    service.getExecutions('9', 3, 50);
    const [, opts] = api.get.mock.calls[0];
    expect(opts.params.get('page')).toBe('3');
    expect(opts.params.get('size')).toBe('50');
  });

  it('runManually() POSTs parameters (null when omitted)', () => {
    service.runManually('9');
    expect(api.post).toHaveBeenCalledWith('/automations/9/run', { parameters: null });
    service.runManually('9', { a: 1 });
    expect(api.post).toHaveBeenCalledWith('/automations/9/run', { parameters: { a: 1 } });
  });

  it('export() GETs the export endpoint', () => {
    service.export();
    expect(api.get).toHaveBeenCalledWith('/automations/export');
  });

  it('import() POSTs the payload to the import endpoint', () => {
    const data = [{ name: 'A' }] as never;
    service.import(data);
    expect(api.post).toHaveBeenCalledWith('/automations/import', data);
  });

  it('getTestModeResults() GETs results with paging, omitting feedback when absent', () => {
    service.getTestModeResults('9');
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe('/automations/9/test-mode/results');
    expect(opts.params.get('feedback')).toBeNull();
  });

  it('getTestModeResults() includes the feedback filter when provided', () => {
    service.getTestModeResults('9', 'CORRECT', 1, 10);
    const [, opts] = api.get.mock.calls[0];
    expect(opts.params.get('feedback')).toBe('CORRECT');
    expect(opts.params.get('page')).toBe('1');
    expect(opts.params.get('size')).toBe('10');
  });

  it('submitTestModeFeedback() PATCHes the feedback path (note nulled when omitted)', () => {
    service.submitTestModeFeedback('9', 'r1', 'INCORRECT');
    expect(api.patch).toHaveBeenCalledWith(
      '/automations/9/test-mode/results/r1/feedback',
      { feedback: 'INCORRECT', note: null },
    );
  });

  it('getTestModeStats() GETs the stats path', () => {
    service.getTestModeStats('9');
    expect(api.get).toHaveBeenCalledWith('/automations/9/test-mode/stats');
  });

  it('clearTestModeResults() DELETEs the results collection', () => {
    service.clearTestModeResults('9');
    expect(api.delete).toHaveBeenCalledWith('/automations/9/test-mode/results');
  });

  it('simulateEmail() POSTs to the simulate path with an empty body', () => {
    service.simulateEmail('9', 'e1');
    expect(api.post).toHaveBeenCalledWith('/automations/9/test-mode/simulate/e1', {});
  });

  it('deleteTestModeResult() DELETEs a single result', () => {
    service.deleteTestModeResult('9', 'r1');
    expect(api.delete).toHaveBeenCalledWith('/automations/9/test-mode/results/r1');
  });
});
