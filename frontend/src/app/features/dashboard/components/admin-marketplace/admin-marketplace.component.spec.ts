import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminMarketplaceComponent } from './admin-marketplace.component';
import { I18nService } from '../../../../core/services/i18n.service';
import { FormatService } from '../../../../core/services/format.service';
import { AdminIdentityService } from '../../../../core/services/admin-identity.service';
import { AdminMarketplaceService } from '../../../../core/services/admin-marketplace.service';
import { ConfirmDialogService } from '../../../../shared/services/confirm-dialog.service';

/**
 * Admin Marketplace Moderation: logic-only spec following the admin-gdpr exemplar. The component is
 * built via TestBed (template auto-renders); ngOnInit is fired explicitly for init-load assertions.
 * Display helpers, computeds, list/filter/paginate flows and the gated moderation actions are
 * exercised directly. Permission-gated paths use a fresh build because `canModerate()` memoizes.
 */
describe('AdminMarketplaceComponent', () => {
  let svc: Record<string, ReturnType<typeof vi.fn>>;
  let identity: { has: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let cmp: AdminMarketplaceComponent;

  function build(opts: { has?: () => boolean } = {}) {
    svc = {
      listings: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
      reviews: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0 })),
      kpis: vi.fn(() => of({ pendingListings: 0, pendingReviews: 0 })),
      getListing: vi.fn(() => of({ listing: { id: 'l1', name: 'X' }, description: 'd', reviews: [] })),
      takeDown: vi.fn(() => of({ id: 'l1', name: 'X', status: 'TAKEN_DOWN' })),
      restore: vi.fn(() => of({ id: 'l1', name: 'X', status: 'PUBLIC' })),
      feature: vi.fn(() => of({ id: 'l1', name: 'X', featured: true })),
      unfeature: vi.fn(() => of({ id: 'l1', name: 'X', featured: false })),
      hideReview: vi.fn(() => of({ id: 'rv1', hidden: true })),
      unhideReview: vi.fn(() => of({ id: 'rv1', hidden: false })),
      deleteReview: vi.fn(() => of(undefined)),
    };
    identity = { has: vi.fn(opts.has ?? (() => true)) };
    confirm = { confirm: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      imports: [AdminMarketplaceComponent],
      providers: [
        provideRouter([]),
        { provide: I18nService, useValue: { t: (k: string) => k } },
        { provide: FormatService, useValue: {} },
        { provide: AdminIdentityService, useValue: identity },
        { provide: AdminMarketplaceService, useValue: svc },
        { provide: ConfirmDialogService, useValue: confirm },
      ],
    });
    cmp = TestBed.createComponent(AdminMarketplaceComponent).componentInstance;
  }

  beforeEach(() => build());
  afterEach(() => vi.restoreAllMocks());

  it('loads kpis + listings on init (default segment)', () => {
    cmp.ngOnInit(); // createComponent renders the template but does not fire ngOnInit
    expect(svc['kpis']).toHaveBeenCalled();
    expect(svc['listings']).toHaveBeenCalled();
    expect(cmp.segment()).toBe('listings');
    expect(cmp.updatedAt()).not.toBe('');
  });

  it('display helpers: initial / statusTone / statusLabel / kindTone / priceLabel / ratingText', () => {
    expect(cmp.initial('alice')).toBe('A');
    expect(cmp.initial('')).toBe('?');

    expect(cmp.statusTone('TAKEN_DOWN' as never)).toBe('danger');
    expect(cmp.statusTone('PAUSED' as never)).toBe('amber');
    expect(cmp.statusTone('PRIVATE' as never)).toBe('slate');
    expect(cmp.statusTone('PUBLIC' as never)).toBe('green');

    expect(cmp.statusLabel('TAKEN_DOWN' as never)).toBe('mod_status_takendown');
    expect(cmp.statusLabel('PUBLIC' as never)).toBe('mod_status_public');

    expect(cmp.kindTone('INTEGRATION')).toBe('slate');
    expect(cmp.kindTone('AUTOMATION')).toBe('violet');

    expect(cmp.priceLabel({ pricingModel: 'FREE', price: 0 })).toBe('mod_free');
    expect(cmp.priceLabel({ pricingModel: 'MONTHLY', price: 0 })).toBe('mod_free'); // !price → free
    expect(cmp.priceLabel({ pricingModel: 'MONTHLY', price: 9.5 })).toBe('€9.50');

    expect(cmp.ratingText(4.567, 12)).toBe('4.6 (12)');
    expect(cmp.ratingText(0, 0)).toBe('0.0 (0)');
  });

  it('avatarHue is a deterministic 0..359 hue', () => {
    const h = cmp.avatarHue('author@x.com');
    expect(h).toBeGreaterThanOrEqual(0);
    expect(h).toBeLessThan(360);
    expect(cmp.avatarHue('author@x.com')).toBe(h);
  });

  it('canModerate / pendingTotal / lHasFilters / rHasFilters computeds', () => {
    expect(cmp.canModerate()).toBe(true);

    expect(cmp.pendingTotal()).toBe(0);
    cmp.kpis.set({ pendingListings: 2, pendingReviews: 3 } as never);
    expect(cmp.pendingTotal()).toBe(5);

    expect(cmp.lHasFilters()).toBe(false);
    cmp.lStatus.set('PAUSED');
    expect(cmp.lHasFilters()).toBe(true);

    expect(cmp.rHasFilters()).toBe(false);
    cmp.rRating.set(2);
    expect(cmp.rHasFilters()).toBe(true);
  });

  it('loadListings stores the page payload', () => {
    svc['listings'].mockReturnValue(of({ content: [{ id: 'l1' }], totalElements: 1, totalPages: 1 }));
    cmp.loadListings();
    expect(cmp.lRows().length).toBe(1);
    expect(cmp.lTotal()).toBe(1);
    expect(cmp.lTotalPages()).toBe(1);
    expect(cmp.lLoading()).toBe(false);
  });

  it('loadReviews stores the page payload', () => {
    svc['reviews'].mockReturnValue(of({ content: [{ id: 'rv1' }], totalElements: 1, totalPages: 1 }));
    cmp.loadReviews();
    expect(cmp.rRows().length).toBe(1);
    expect(cmp.rTotal()).toBe(1);
    expect(cmp.rLoading()).toBe(false);
  });

  it('setSegment switches + lazy-loads the target segment only once', () => {
    cmp.setSegment('reviews');
    expect(cmp.segment()).toBe('reviews');
    expect(svc['reviews']).toHaveBeenCalledTimes(1);
    // already-loaded rows present → switching back should not refetch listings
    cmp.lRows.set([{ id: 'l1' }] as never);
    cmp.setSegment('listings');
    expect(svc['listings']).not.toHaveBeenCalled();
  });

  it('lClearFilters resets every listing filter and reloads', () => {
    cmp.lSearch.set('a'); cmp.lStatus.set('PAUSED'); cmp.lKind.set('INTEGRATION'); cmp.lPricing.set('PAID');
    cmp.lClearFilters();
    expect(cmp.lSearch()).toBe('');
    expect(cmp.lStatus()).toBe('');
    expect(cmp.lKind()).toBe('');
    expect(cmp.lPricing()).toBe('');
    expect(svc['listings']).toHaveBeenCalled();
  });

  it('lGoToPage / rGoToPage clamp to the valid range', () => {
    cmp.lTotalPages.set(3);
    cmp.lGoToPage(5);
    expect(cmp.lPage()).toBe(0);
    cmp.lGoToPage(2);
    expect(cmp.lPage()).toBe(2);

    cmp.rTotalPages.set(2);
    cmp.rGoToPage(-1);
    expect(cmp.rPage()).toBe(0);
    cmp.rGoToPage(1);
    expect(cmp.rPage()).toBe(1);
  });

  it('openListing seeds + loads the listing detail', () => {
    cmp.openListing({ id: 'l1', name: 'X' } as never);
    expect(svc['getListing']).toHaveBeenCalledWith('l1');
    expect(cmp.detailListing()?.listing.id).toBe('l1');
    expect(cmp.detailListingLoading()).toBe(false);
    expect(cmp.listingTab()).toBe('overview');
  });

  it('openReview / closeDetail / setListingTab toggle the modal state', () => {
    cmp.openReview({ id: 'rv1' } as never);
    expect(cmp.detailReview()?.id).toBe('rv1');
    expect(cmp.detailListing()).toBeNull();
    cmp.setListingTab('reviews');
    expect(cmp.listingTab()).toBe('reviews');
    cmp.closeDetail();
    expect(cmp.detailReview()).toBeNull();
    expect(cmp.detailListing()).toBeNull();
  });

  it('toggleMenu opens then closes for the same row id', () => {
    const evt = {
      stopPropagation: vi.fn(),
      currentTarget: { getBoundingClientRect: () => ({ bottom: 10, right: 200 }) },
    } as unknown as Event;
    cmp.toggleMenu('l1', evt);
    expect(cmp.openMenuId()).toBe('l1');
    expect(cmp.menuPos()).not.toBeNull();
    cmp.toggleMenu('l1', evt);
    expect(cmp.openMenuId()).toBeNull();
  });

  it('takeDown moderates with the entered reason when allowed', () => {
    vi.spyOn(window, 'prompt').mockReturnValue('spam');
    cmp.lRows.set([{ id: 'l1', name: 'X', status: 'PUBLIC' }] as never);
    cmp.takeDown({ id: 'l1', name: 'X' } as never);
    expect(svc['takeDown']).toHaveBeenCalledWith('l1', 'spam');
    expect(cmp.lRows()[0].status).toBe('TAKEN_DOWN');
    expect(cmp.busy()).toBe(false);
  });

  it('takeDown aborts when the reason prompt is cancelled', () => {
    vi.spyOn(window, 'prompt').mockReturnValue(null);
    cmp.takeDown({ id: 'l1', name: 'X' } as never);
    expect(svc['takeDown']).not.toHaveBeenCalled();
  });

  it('takeDown is blocked without moderate permission', () => {
    TestBed.resetTestingModule();
    build({ has: () => false });
    vi.spyOn(window, 'prompt').mockReturnValue('spam');
    cmp.takeDown({ id: 'l1', name: 'X' } as never);
    expect(svc['takeDown']).not.toHaveBeenCalled();
  });

  it('restore requires confirmation', async () => {
    confirm.confirm.mockResolvedValue(false);
    await cmp.restore({ id: 'l1', name: 'X' } as never);
    expect(svc['restore']).not.toHaveBeenCalled();
    confirm.confirm.mockResolvedValue(true);
    await cmp.restore({ id: 'l1', name: 'X' } as never);
    expect(svc['restore']).toHaveBeenCalledWith('l1');
  });

  it('setFeatured calls feature / unfeature based on the flag', () => {
    cmp.setFeatured({ id: 'l1', name: 'X' } as never, true);
    expect(svc['feature']).toHaveBeenCalledWith('l1');
    cmp.setFeatured({ id: 'l1', name: 'X' } as never, false);
    expect(svc['unfeature']).toHaveBeenCalledWith('l1');
  });

  it('setReviewHidden calls hide / unhide based on the flag', () => {
    cmp.setReviewHidden({ id: 'rv1' } as never, true);
    expect(svc['hideReview']).toHaveBeenCalledWith('rv1');
    cmp.setReviewHidden({ id: 'rv1' } as never, false);
    expect(svc['unhideReview']).toHaveBeenCalledWith('rv1');
  });

  it('deleteReview removes the row with the entered reason', () => {
    vi.spyOn(window, 'prompt').mockReturnValue('abusive');
    cmp.rRows.set([{ id: 'rv1' }, { id: 'rv2' }] as never);
    cmp.deleteReview({ id: 'rv1' } as never);
    expect(svc['deleteReview']).toHaveBeenCalledWith('rv1', 'abusive');
    expect(cmp.rRows().map(r => r.id)).toEqual(['rv2']);
  });

  it('deleteReview is blocked without moderate permission', () => {
    TestBed.resetTestingModule();
    build({ has: () => false });
    vi.spyOn(window, 'prompt').mockReturnValue('abusive');
    cmp.deleteReview({ id: 'rv1' } as never);
    expect(svc['deleteReview']).not.toHaveBeenCalled();
  });
});
