import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { AdminMarketplaceService } from './admin-marketplace.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('AdminMarketplaceService', () => {
  let api: MockApi;
  let service: AdminMarketplaceService;
  const base = '/admin/marketplace';

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(AdminMarketplaceService);
  });

  it('listings()/reviews() GET with pageable + filter params', () => {
    service.listings({ status: 'LIVE' } as never, 1, 10);
    expect(api.get.mock.calls[0][0]).toBe(`${base}/listings`);
    expect((api.get.mock.calls[0][1].params as HttpParams).get('status')).toBe('LIVE');
    service.reviews({ flagged: true } as never, 0, 10);
    expect(api.get.mock.calls[1][0]).toBe(`${base}/reviews`);
    expect((api.get.mock.calls[1][1].params as HttpParams).get('flagged')).toBe('true');
  });

  it('kpis()/getListing() hit kpis and detail', () => {
    service.kpis();
    expect(api.get).toHaveBeenCalledWith(`${base}/kpis`);
    service.getListing('l1');
    expect(api.get).toHaveBeenCalledWith(`${base}/listings/l1`);
  });

  it('listing moderation POSTs takedown(reason) / restore / feature / unfeature', () => {
    service.takeDown('l1', 'spam');
    expect(api.post).toHaveBeenCalledWith(`${base}/listings/l1/takedown`, { reason: 'spam' });
    service.restore('l1');
    expect(api.post).toHaveBeenCalledWith(`${base}/listings/l1/restore`, {});
    service.feature('l1');
    expect(api.post).toHaveBeenCalledWith(`${base}/listings/l1/feature`, {});
    service.unfeature('l1');
    expect(api.post).toHaveBeenCalledWith(`${base}/listings/l1/unfeature`, {});
  });

  it('review moderation: hide / unhide / delete (URL-encoded reason)', () => {
    service.hideReview('rv1');
    expect(api.post).toHaveBeenCalledWith(`${base}/reviews/rv1/hide`, {});
    service.unhideReview('rv1');
    expect(api.post).toHaveBeenCalledWith(`${base}/reviews/rv1/unhide`, {});
    service.deleteReview('rv1', null);
    expect(api.delete).toHaveBeenCalledWith(`${base}/reviews/rv1`);
    service.deleteReview('rv1', 'abusive language');
    expect(api.delete).toHaveBeenCalledWith(`${base}/reviews/rv1?reason=abusive%20language`);
  });
});
