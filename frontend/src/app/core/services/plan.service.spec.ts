import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { PlanService } from './plan.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

/** Builds a minimal UsageResponse-shaped object for the cost computeds. */
function usage(costLimitCents: number, costUsedCents: number, costUsedMicros?: number) {
  return { plan: { costLimitCents }, usage: { costUsedCents, costUsedMicros } } as never;
}

describe('PlanService', () => {
  let api: MockApi;
  let service: PlanService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(PlanService);
  });

  it('getPlans() GETs the plans endpoint', () => {
    service.getPlans();
    expect(api.get).toHaveBeenCalledWith('/users/plans');
  });

  it('getUsage() GETs the usage endpoint', () => {
    service.getUsage();
    expect(api.get).toHaveBeenCalledWith('/users/me/usage');
  });

  it('loadUsage() caches the response and clears the loading flag', () => {
    const u = usage(100, 50);
    api.get.mockReturnValue(of(u));
    service.loadUsage();
    expect(service.usage()).toBe(u);
    expect(service.loadingUsage()).toBe(false);
  });

  it('loadUsage() clears the loading flag on error', () => {
    api.get.mockReturnValue({ subscribe: (o: { error: () => void }) => o.error() } as never);
    service.loadUsage();
    expect(service.loadingUsage()).toBe(false);
  });

  describe('costUnlimited', () => {
    it('is true only when the cap is the -1 sentinel', () => {
      expect(service.costUnlimited()).toBe(false); // no usage yet
      service.usage.set(usage(-1, 0));
      expect(service.costUnlimited()).toBe(true);
      service.usage.set(usage(500, 0));
      expect(service.costUnlimited()).toBe(false);
    });
  });

  describe('costUsagePercent', () => {
    it('is null without usage, or with an unlimited (-1) / disabled (0) cap', () => {
      expect(service.costUsagePercent()).toBeNull();
      service.usage.set(usage(-1, 10));
      expect(service.costUsagePercent()).toBeNull();
      service.usage.set(usage(0, 10));
      expect(service.costUsagePercent()).toBeNull();
    });

    it('computes the percentage from costUsedMicros when present', () => {
      // cap 100 cents → 1,000,000 micros; used 250,000 micros → 25%
      service.usage.set(usage(100, 0, 250_000));
      expect(service.costUsagePercent()).toBeCloseTo(25);
    });

    it('falls back to costUsedCents (× micros-per-cent) when micros are absent', () => {
      // cap 100 cents, used 50 cents → 50%
      service.usage.set(usage(100, 50));
      expect(service.costUsagePercent()).toBeCloseTo(50);
    });
  });
});
