import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AdminService } from './admin.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminService', () => {
  let api: MockApi;
  let service: AdminService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminService);
  });

  const lastParams = (call = 0) => api.get.mock.calls[call][1].params as HttpParams;

  it('getStats() GETs the stats endpoint', () => {
    service.getStats();
    expect(api.get).toHaveBeenCalledWith('/admin/stats');
  });

  it('getUsers() sets search/page/size and only the provided filters', () => {
    service.getUsers('joe', 'ADMIN', 'ACTIVE', 'pro', 2, 50);
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe('/admin/users');
    const p = opts.params as HttpParams;
    expect(p.get('search')).toBe('joe');
    expect(p.get('page')).toBe('2');
    expect(p.get('size')).toBe('50');
    expect(p.get('role')).toBe('ADMIN');
    expect(p.get('status')).toBe('ACTIVE');
    expect(p.get('plan')).toBe('pro');
  });

  it('getUsers() omits role/status/plan when not given', () => {
    service.getUsers();
    const p = lastParams();
    expect(p.get('search')).toBe('');
    expect(p.get('role')).toBeNull();
    expect(p.get('status')).toBeNull();
    expect(p.get('plan')).toBeNull();
  });

  it('getUser() GETs a single user', () => {
    service.getUser('u1');
    expect(api.get).toHaveBeenCalledWith('/admin/users/u1');
  });

  it('user mutations PATCH the right sub-paths', () => {
    service.updateRole('u1', 'ADMIN');
    expect(api.patch).toHaveBeenCalledWith('/admin/users/u1/role', { role: 'ADMIN' });
    service.updateStaffRole('u1', 'SUPPORT');
    expect(api.patch).toHaveBeenCalledWith('/admin/users/u1/staff-role', { staffRole: 'SUPPORT' });
    service.updateStaffRole('u1', null);
    expect(api.patch).toHaveBeenCalledWith('/admin/users/u1/staff-role', { staffRole: null });
    service.assignPlan('u1', 'p1');
    expect(api.patch).toHaveBeenCalledWith('/admin/users/u1/plan', { planId: 'p1' });
    service.disableUser('u1');
    expect(api.patch).toHaveBeenCalledWith('/admin/users/u1/disable', {});
  });

  it('getUserOrganizations()/getUserMailboxes() GET the detail tabs', () => {
    service.getUserOrganizations('u1');
    expect(api.get).toHaveBeenCalledWith('/admin/users/u1/organizations');
    service.getUserMailboxes('u1');
    expect(api.get).toHaveBeenCalledWith('/admin/users/u1/mailboxes');
  });

  it('staff notes CRUD hit the notes sub-collection', () => {
    service.getUserNotes('u1');
    expect(api.get).toHaveBeenCalledWith('/admin/users/u1/notes');
    service.addUserNote('u1', 'hi');
    expect(api.post).toHaveBeenCalledWith('/admin/users/u1/notes', { body: 'hi' });
    service.deleteUserNote('u1', 'n1');
    expect(api.delete).toHaveBeenCalledWith('/admin/users/u1/notes/n1');
  });

  it('session tooling hits reset-password / sessions / revoke-sessions', () => {
    service.resetUserPassword('u1');
    expect(api.post).toHaveBeenCalledWith('/admin/users/u1/reset-password', {});
    service.getUserSessions('u1');
    expect(api.get).toHaveBeenCalledWith('/admin/users/u1/sessions');
    service.revokeUserSessions('u1');
    expect(api.post).toHaveBeenCalledWith('/admin/users/u1/revoke-sessions', {});
  });

  it('AI usage endpoints (stats / by-user / timeline param)', () => {
    service.getAiUsageStats();
    expect(api.get).toHaveBeenCalledWith('/admin/ai-usage');
    service.getAiUsageByUser();
    expect(api.get).toHaveBeenCalledWith('/admin/ai-usage/by-user');
    service.getAiUsageTimeline('weekly');
    const [path, opts] = api.get.mock.calls[2];
    expect(path).toBe('/admin/ai-usage/timeline');
    expect((opts.params as HttpParams).get('period')).toBe('weekly');
  });

  it('automation stats + executions paging', () => {
    service.getAutomationStats();
    expect(api.get).toHaveBeenCalledWith('/admin/automations/stats');
    service.getAutomationExecutions(1, 5);
    const [path, opts] = api.get.mock.calls[1];
    expect(path).toBe('/admin/automations/executions');
    expect((opts.params as HttpParams).get('page')).toBe('1');
    expect((opts.params as HttpParams).get('size')).toBe('5');
  });

  it('getAuditLog() applies only the provided filters', () => {
    service.getAuditLog('u1', 'DELETE', 0, 20, 'org1');
    const p = lastParams();
    expect(p.get('user')).toBe('u1');
    expect(p.get('action')).toBe('DELETE');
    expect(p.get('organizationId')).toBe('org1');
  });

  it('exportAuditLogCsv() downloads a blob with optional filters', () => {
    service.exportAuditLogCsv('u1');
    const [path, opts] = api.getBlob.mock.calls[0];
    expect(path).toBe('/admin/audit-log/export');
    expect((opts.params as HttpParams).get('user')).toBe('u1');
  });

  it('plan CRUD hits /admin/plans', () => {
    const req = { name: 'Pro' } as never;
    service.getPlans();
    expect(api.get).toHaveBeenCalledWith('/admin/plans');
    service.createPlan(req);
    expect(api.post).toHaveBeenCalledWith('/admin/plans', req);
    service.updatePlan('p1', req);
    expect(api.put).toHaveBeenCalledWith('/admin/plans/p1', req);
    service.deletePlan('p1');
    expect(api.delete).toHaveBeenCalledWith('/admin/plans/p1');
  });

  it('model-pricing CRUD hits /admin/pricing/models', () => {
    const req = { model: 'opus' } as never;
    service.getModelPricing();
    expect(api.get).toHaveBeenCalledWith('/admin/pricing/models');
    service.createModelPricing(req);
    expect(api.post).toHaveBeenCalledWith('/admin/pricing/models', req);
    service.updateModelPricing('m1', req);
    expect(api.put).toHaveBeenCalledWith('/admin/pricing/models/m1', req);
    service.deleteModelPricing('m1');
    expect(api.delete).toHaveBeenCalledWith('/admin/pricing/models/m1');
  });
});
