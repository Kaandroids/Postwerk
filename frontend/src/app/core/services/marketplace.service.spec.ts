import { TestBed } from '@angular/core/testing';
import { HttpParams } from '@angular/common/http';
import { MarketplaceService, pricingLabel } from './marketplace.service';
import { createMockApi, MockApi, provideMockApi } from '../../../testing';

describe('MarketplaceService', () => {
  let api: MockApi;
  let service: MarketplaceService;

  beforeEach(() => {
    api = createMockApi();
    TestBed.configureTestingModule({ providers: [provideMockApi(api)] });
    service = TestBed.inject(MarketplaceService);
  });

  it('discover() GETs listings with no params by default', () => {
    service.discover();
    const [path, opts] = api.get.mock.calls[0];
    expect(path).toBe('/marketplace/listings');
    expect((opts.params as HttpParams).keys()).toEqual([]);
  });

  it('discover() sets cat/sort/q params when provided', () => {
    service.discover({ cat: 'sales', sort: 'POPULAR' as never, q: 'invoice' });
    const [, opts] = api.get.mock.calls[0];
    const p = opts.params as HttpParams;
    expect(p.get('cat')).toBe('sales');
    expect(p.get('sort')).toBe('POPULAR');
    expect(p.get('q')).toBe('invoice');
  });

  it('getDetail() GETs a single listing', () => {
    service.getDetail('l1');
    expect(api.get).toHaveBeenCalledWith('/marketplace/listings/l1');
  });

  it('publish() POSTs the request', () => {
    const req = { automationId: 'a1' } as never;
    service.publish(req);
    expect(api.post).toHaveBeenCalledWith('/marketplace/listings', req);
  });

  it('install() POSTs to the install sub-path', () => {
    service.install('l1');
    expect(api.post).toHaveBeenCalledWith('/marketplace/listings/l1/install', {});
  });

  it('unpublish() DELETEs the listing', () => {
    service.unpublish('l1');
    expect(api.delete).toHaveBeenCalledWith('/marketplace/listings/l1');
  });

  it('library() GETs the buyer library', () => {
    service.library();
    expect(api.get).toHaveBeenCalledWith('/marketplace/library');
  });

  it('saveConstants() PUTs constants for an acquisition', () => {
    const constants = [{ name: 'x' }] as never;
    service.saveConstants('acq1', constants);
    expect(api.put).toHaveBeenCalledWith('/marketplace/acquisitions/acq1/constants', { constants });
  });

  it('bindAccounts() PUTs the account ids', () => {
    service.bindAccounts('acq1', ['e1', 'e2']);
    expect(api.put).toHaveBeenCalledWith('/marketplace/acquisitions/acq1/accounts', { accountIds: ['e1', 'e2'] });
  });

  it('activate() POSTs to the activate sub-path', () => {
    service.activate('acq1');
    expect(api.post).toHaveBeenCalledWith('/marketplace/acquisitions/acq1/activate', {});
  });

  it('reviews() GETs and addReview() POSTs to the reviews sub-path', () => {
    service.reviews('l1');
    expect(api.get).toHaveBeenCalledWith('/marketplace/listings/l1/reviews');
    const review = { rating: 5 } as never;
    service.addReview('l1', review);
    expect(api.post).toHaveBeenCalledWith('/marketplace/listings/l1/reviews', review);
  });
});

describe('pricingLabel', () => {
  const t = (k: string) => k; // echo translator

  it('returns the i18n key for FREE and FREEMIUM regardless of price', () => {
    expect(pricingLabel('FREE', 999, t)).toBe('mkt_price_free');
    expect(pricingLabel('FREEMIUM', 999, t)).toBe('mkt_price_freemium');
  });

  it('renders a euro amount with a per-month suffix for MONTHLY', () => {
    expect(pricingLabel('MONTHLY', 9, t)).toBe('€9 mkt_per_month');
  });

  it('renders a euro amount with a per-year suffix for YEARLY', () => {
    expect(pricingLabel('YEARLY', 90, t)).toBe('€90 mkt_per_year');
  });

  it('renders a bare amount for ONE_TIME', () => {
    expect(pricingLabel('ONE_TIME', 49, t)).toBe('€49');
  });

  it('falls back to the model label key when price is missing', () => {
    expect(pricingLabel('ONE_TIME', null, t)).toBe('mkt_price_one_time');
  });
});
