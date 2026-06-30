import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AdminIdentityService } from './admin-identity.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminIdentityService', () => {
  let api: MockApi;
  let service: AdminIdentityService;
  const identity = { staffRole: 'SUPPORT', permissions: ['USER_VIEW', 'USER_MANAGE'] };

  beforeEach(() => {
    api = createMockApi();
    api.get.mockReturnValue(of(identity));
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminIdentityService);
  });

  it('load() fetches /admin/me once and caches the identity', () => {
    service.load().subscribe();
    service.load().subscribe();
    expect(api.get).toHaveBeenCalledTimes(1);
    expect(api.get).toHaveBeenCalledWith('/admin/me');
    expect(service.identity()).toEqual(identity);
  });

  it('derives isStaff / staffRole from the loaded identity', () => {
    expect(service.isStaff()).toBe(false);
    expect(service.staffRole()).toBeNull();
    service.load().subscribe();
    expect(service.isStaff()).toBe(true);
    expect(service.staffRole()).toBe('SUPPORT');
  });

  it('has() reflects the role permission set', () => {
    service.load().subscribe();
    expect(service.has('USER_MANAGE')).toBe(true);
    expect(service.has('BILLING_MANAGE')).toBe(false);
  });

  it('returns the cached value synchronously on subsequent loads', () => {
    service.load().subscribe();
    let emitted: unknown;
    service.load().subscribe(v => (emitted = v));
    expect(emitted).toEqual(identity);
    expect(api.get).toHaveBeenCalledTimes(1);
  });

  it('clear() drops the cache so the next load re-fetches', () => {
    service.load().subscribe();
    service.clear();
    expect(service.identity()).toBeNull();
    service.load().subscribe();
    expect(api.get).toHaveBeenCalledTimes(2);
  });

  it('re-allows a fetch after a transient load error', () => {
    api.get.mockReturnValueOnce(throwError(() => new Error('boom')));
    service.load().subscribe({ error: () => {} });
    expect(service.identity()).toBeNull();
    // request$ was cleared on error → the next load issues a fresh (now succeeding) request.
    service.load().subscribe();
    expect(service.identity()).toEqual(identity);
    expect(api.get).toHaveBeenCalledTimes(2);
  });
});
